package com.taosdata.demo;

import com.taosdata.demo.common.InsertTask;
import com.taosdata.demo.pool.C3p0Builder;
import com.taosdata.demo.pool.DbcpBuilder;
import com.taosdata.demo.pool.DruidPoolBuilder;
import com.taosdata.demo.pool.HikariCpBuilder;
import org.apache.log4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ConnectionPoolDemo {

    private static Logger logger = Logger.getLogger(DruidPoolBuilder.class);
    private static final String dbName = "pool_test";

    private static int batchSize = 10;
    private static int sleep = 1000;
    private static int connectionPoolSize = 50;
    private static int tableSize = 1000;
    private static int threadPoolSize = 50;
    private static String poolType = "hikari";


    public static void main(String[] args) throws InterruptedException {
        String host = null;
        for (int i = 0; i < args.length; i++) {
            if ("-host".equalsIgnoreCase(args[i]) && i < args.length - 1) {
                host = args[++i];
            }
            if ("-batchSize".equalsIgnoreCase(args[i]) && i < args.length - 1) {
                batchSize = Integer.parseInt(args[++i]);
            }
            if ("-sleep".equalsIgnoreCase(args[i]) && i < args.length - 1) {
                sleep = Integer.parseInt(args[++i]);
            }
            if ("-connectPoolSize".equalsIgnoreCase(args[i]) && i < args.length - 1) {
                connectionPoolSize = Integer.parseInt(args[++i]);
            }
            if ("-tableSize".equalsIgnoreCase(args[i]) && i < args.length - 1) {
                tableSize = Integer.parseInt(args[++i]);
            }
            if ("-poolType".equalsIgnoreCase(args[i]) && i < args.length - 1) {
                poolType = args[++i];
            }
            if ("-threadPoolSize".equalsIgnoreCase(args[i]) && i < args.length - 1) {
                threadPoolSize = Integer.parseInt(args[++i]);
            }
        }
        if (host == null) {
            System.out.println("Usage: java -jar XXX.jar " +
                    "-host <hostname> " +
                    "-batchSize <batchSize> " +
                    "-sleep <sleep> " +
                    "-connectionPoolSize <connectionPoolSize> " +
                    "-tableSize <tableSize>" +
                    "-threadPoolSize <threadPoolSize>" +
                    "-poolType <c3p0| dbcp| druid| hikari>");
            return;
        }

        DataSource dataSource;
        switch (poolType) {
            case "c3p0":
                dataSource = C3p0Builder.getDataSource(host, connectionPoolSize);
                break;
            case "dbcp":
                dataSource = DbcpBuilder.getDataSource(host, connectionPoolSize);
                break;
            case "druid":
                dataSource = DruidPoolBuilder.getDataSource(host, connectionPoolSize);
                break;
            case "hikari":
            default:
                dataSource = HikariCpBuilder.getDataSource(host, connectionPoolSize);
                poolType = "hikari";
        }

        logger.info(">>>>>>>>>>>>>> connection pool Type: " + poolType);
        init(dataSource);

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadPoolSize);
        while (true) {
            executor.execute(new InsertTask(dataSource, dbName, tableSize, batchSize));
            logger.info("thread pool size : " + executor.getPoolSize() + ", active pool size: " + executor.getActiveCount());
            if (sleep > 0)
                TimeUnit.MILLISECONDS.sleep(sleep);
        }
    }

    private static void init(DataSource dataSource) {

        try (Connection conn = dataSource.getConnection()) {
            execute(conn, "drop database if exists " + dbName + "");
            execute(conn, "create database if not exists " + dbName + "");
            execute(conn, "use " + dbName + "");
            execute(conn, "create table weather(ts timestamp, temperature float, humidity int) tags(location nchar(64), groupId int)");
            for (int tb_ind = 1; tb_ind <= tableSize; tb_ind++) {
                execute(conn, "create table t_" + tb_ind + " using weather tags('beijing'," + (tb_ind + 1) + ")");
            }
            logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>> init finished.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void execute(Connection con, String sql) {
        try (Statement stmt = con.createStatement()) {
            stmt.executeUpdate(sql);
            logger.info("SQL >>> " + sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
