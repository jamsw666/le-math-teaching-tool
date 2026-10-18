package com.javalearning.middleware;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 8-1 JDBC 与连接池。
 *
 * <p>演示：JDBC 标准流程（真实用 H2 执行）、PreparedStatement 防注入、事务 commit/rollback、
 * 批处理，以及手写一个 ~60 行的迷你连接池理解 HikariCP 的核心思想。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.middleware.JdbcConnectionPoolDemo
 */
public class JdbcConnectionPoolDemo {

    private static final String URL = "jdbc:h2:mem:jdbcdemo;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        basicCrud();
        transactionDemo();
        batchDemo();
        connectionPoolDemo();
        printSummary();
    }

    // ---------------- JDBC 基本流程 ----------------
    static void basicCrud() throws SQLException {
        System.out.println("== JDBC 基本流程 ==");
        // JDBC 4.0+ 通过 SPI（META-INF/services/java.sql.Driver）自动注册驱动，不再需要 Class.forName
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE account(id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(32), balance DECIMAL(10,2))");
            }
            String sql = "INSERT INTO account(name, balance) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "alice");
                ps.setBigDecimal(2, new java.math.BigDecimal("100.00"));
                ps.executeUpdate();
                ps.setString(1, "bob");
                ps.setBigDecimal(2, new java.math.BigDecimal("50.00"));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT id, name, balance FROM account WHERE name = ?")) {
                // 即使传入恶意字符串，也只会被当作普通值
                ps.setString(1, "' OR '1'='1");
                try (ResultSet rs = ps.executeQuery()) {
                    System.out.println("  注入尝试返回行数: " + (rs.next() ? 1 : 0) + "（PreparedStatement 预编译，参数不会拼进 SQL）");
                }
            }
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT id, name, balance FROM account ORDER BY id")) {
                while (rs.next()) {
                    System.out.printf("  %d %-6s %s%n", rs.getInt("id"), rs.getString("name"), rs.getBigDecimal("balance"));
                }
            }
        }
    }

    // ---------------- 事务 ----------------
    static void transactionDemo() throws SQLException {
        System.out.println("\n== 事务：转账失败回滚 ==");
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            conn.setAutoCommit(false); // 开启事务
            try {
                update(conn, "UPDATE account SET balance = balance - 80 WHERE name = 'alice'");
                checkBalance(conn, "alice");
                update(conn, "UPDATE account SET balance = balance + 80 WHERE name = 'carol'"); // carol 不存在 -> 0 行
                int rows = countRows(conn, "SELECT COUNT(*) FROM account WHERE name = 'carol'");
                if (rows == 0) {
                    throw new IllegalStateException("收款账户不存在");
                }
                conn.commit();
            } catch (Exception e) {
                System.out.println("  发生异常: " + e.getMessage() + " -> rollback");
                conn.rollback();
            } finally {
                conn.setAutoCommit(true);
            }
            checkBalance(conn, "alice");
        }
    }

    // ---------------- 批处理 ----------------
    static void batchDemo() throws SQLException {
        System.out.println("\n== 批处理 ==");
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO account(name, balance) VALUES (?, ?)")) {
                for (int i = 0; i < 1000; i++) {
                    ps.setString(1, "user" + i);
                    ps.setBigDecimal(2, java.math.BigDecimal.TEN);
                    ps.addBatch();
                    if (i % 500 == 499) {
                        ps.executeBatch(); // 分批提交避免内存堆积
                    }
                }
            }
            conn.commit();
            System.out.println("  批量插入后总行数: " + countRows(conn, "SELECT COUNT(*) FROM account"));
            System.out.println("  MySQL 需 rewriteBatchedStatements=true 才会真正合并为多值 INSERT");
        }
    }

    // ---------------- 迷你连接池 ----------------
    static void connectionPoolDemo() throws Exception {
        System.out.println("\n== 迷你连接池 ==");
        MiniPool pool = new MiniPool(URL, 3);
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            try (Connection conn = pool.borrow()) {
                countRows(conn, "SELECT 1");
            } // close() 不是真正关闭，而是归还
        }
        long pooled = System.nanoTime() - start;

        start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
                countRows(conn, "SELECT 1");
            }
        }
        long direct = System.nanoTime() - start;
        System.out.printf("  100 次：池化 %.1f ms，每次新建 %.1f ms（内存库差距小，真实网络数据库差一个数量级以上）%n",
                pooled / 1e6, direct / 1e6);
        System.out.println("  真实物理连接只创建了 " + pool.created.get() + " 个");

        // 池耗尽时的等待
        List<Connection> held = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            held.add(pool.borrow());
        }
        try {
            pool.borrow();
        } catch (SQLException e) {
            System.out.println("  第 4 个请求: " + e.getMessage());
        }
        for (Connection c : held) {
            c.close();
        }
        pool.shutdown();
    }

    /** 迷你连接池：阻塞队列保存空闲连接，借出时用代理拦截 close() 变成归还。 */
    static class MiniPool {
        private final BlockingQueue<Connection> idle;
        final AtomicInteger created = new AtomicInteger();
        private final String url;
        private final int maxSize;

        MiniPool(String url, int maxSize) throws SQLException {
            this.url = url;
            this.maxSize = maxSize;
            this.idle = new ArrayBlockingQueue<>(maxSize);
            for (int i = 0; i < maxSize; i++) {
                idle.add(newPhysical());
            }
        }

        private Connection newPhysical() throws SQLException {
            created.incrementAndGet();
            return DriverManager.getConnection(url, "sa", "");
        }

        Connection borrow() throws SQLException {
            try {
                Connection physical = idle.poll(200, TimeUnit.MILLISECONDS); // HikariCP: connectionTimeout
                if (physical == null) {
                    throw new SQLException("连接池耗尽，等待 200ms 超时（maxSize=" + maxSize + "）");
                }
                if (!physical.isValid(1)) { // 借出前校验，HikariCP 用 JDBC4 isValid 或 connectionTestQuery
                    physical = newPhysical();
                }
                Connection target = physical;
                return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                        Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                        (proxy, method, margs) -> {
                            if (method.getName().equals("close")) {
                                if (!target.getAutoCommit()) {
                                    target.rollback(); // 归还前重置状态，避免脏事务泄漏给下一个使用者
                                    target.setAutoCommit(true);
                                }
                                idle.offer(target);
                                return null;
                            }
                            return method.invoke(target, margs);
                        });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("interrupted", e);
            }
        }

        void shutdown() throws SQLException {
            for (Connection c : idle) {
                c.close();
            }
        }
    }

    static void printSummary() {
        System.out.println("""

                JDBC 四步：获取 Connection -> 创建 Statement/PreparedStatement -> 执行并处理 ResultSet -> 关闭（try-with-resources）
                为什么必须用连接池：TCP 三次握手 + 认证 + 初始化会话，建立一个 MySQL 连接通常耗时数毫秒到数十毫秒；
                  数据库侧连接数也是稀缺资源（MySQL 默认 max_connections=151）。
                HikariCP 为什么快：
                  - ConcurrentBag 无锁借还（ThreadLocal 优先复用本线程刚归还的连接）
                  - FastList 替代 ArrayList（去掉 range check，删除时从尾部查找）
                  - 代理类用 Javassist 字节码生成而非反射 Proxy
                  - 极简配置：maximumPoolSize（建议 CPU核数*2 + 磁盘数，常用 10~20）、minimumIdle、connectionTimeout、idleTimeout、maxLifetime（略小于 MySQL wait_timeout）
                Druid 特点：阿里出品，带 SQL 监控面板、防火墙 WallFilter、慢 SQL 统计，功能多但稍重
                常见坑：
                  - 连接泄漏（忘记 close）-> 池很快耗尽；HikariCP leakDetectionThreshold 可定位
                  - 事务中获取两个连接 -> 实际是两个事务（Spring 用 DataSourceUtils 绑定 ThreadLocal 保证同一连接）
                  - maxLifetime > 数据库侧 wait_timeout -> 拿到已被服务端断开的"僵尸连接"
                """);
    }

    private static void update(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            int rows = st.executeUpdate(sql);
            System.out.println("  " + sql + " -> 影响 " + rows + " 行");
        }
    }

    private static int countRows(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void checkBalance(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM account WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                System.out.println("  " + name + " 当前余额: " + rs.getBigDecimal(1));
            }
        }
    }
}
