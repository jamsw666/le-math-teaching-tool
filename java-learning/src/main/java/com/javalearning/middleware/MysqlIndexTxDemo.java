package com.javalearning.middleware;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;

/**
 * 8-2 MySQL：索引 / 事务 / 锁 / 调优。
 *
 * <p>演示：用 H2 真实对比有无索引的查询计划（EXPLAIN）与耗时、复合索引最左前缀、
 * 隔离级别（不可重复读）、行锁等待，以及用 TreeMap 模拟 B+ 树范围查询的思想。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.middleware.MysqlIndexTxDemo
 */
public class MysqlIndexTxDemo {

    private static final String URL = "jdbc:h2:mem:mysqldemo;DB_CLOSE_DELAY=-1;MODE=MySQL;QUERY_CACHE_SIZE=0";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "")) {
            prepareData(conn);
            indexDemo(conn);
            compositeIndexDemo(conn);
        }
        isolationDemo();
        rowLockDemo();
        bPlusTreeIdea();
        printSummary();
    }

    static void prepareData(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE orders(id INT PRIMARY KEY, user_id INT, status VARCHAR(16), amount INT, created DATE)");
        }
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO orders VALUES (?, ?, ?, ?, ?)")) {
            for (int i = 1; i <= 200_000; i++) {
                ps.setInt(1, i);
                ps.setInt(2, i % 5000);
                ps.setString(3, i % 10 == 0 ? "PAID" : "NEW");
                ps.setInt(4, i % 1000);
                ps.setDate(5, java.sql.Date.valueOf("2024-01-01"));
                ps.addBatch();
                if (i % 10_000 == 0) {
                    ps.executeBatch();
                }
            }
        }
        conn.commit();
        conn.setAutoCommit(true);
    }

    static void indexDemo(Connection conn) throws SQLException {
        System.out.println("== 有无索引对比（20 万行） ==");
        String sql = "SELECT COUNT(*) FROM orders WHERE user_id = 1234";
        explain(conn, sql);
        long noIndex = timeQuery(conn, sql);
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX idx_user ON orders(user_id)");
        }
        explain(conn, sql);
        long withIndex = timeQuery(conn, sql);
        System.out.printf("  无索引（全表扫描）%.2f ms  ->  有索引 %.2f ms%n", noIndex / 1e6, withIndex / 1e6);

        System.out.println("\n== 索引失效：在索引列上做函数运算 ==");
        String broken = "SELECT COUNT(*) FROM orders WHERE user_id + 0 = 1234";
        explain(conn, broken);
        System.out.printf("  耗时 %.2f ms：计划里虽出现索引名，但条件无法用于定位（只能扫整个索引），与全表扫描同一量级%n", timeQuery(conn, broken) / 1e6);
        System.out.println("  （MySQL 同理：WHERE DATE(created)=..., WHERE name LIKE '%x', 隐式类型转换 WHERE phone = 138... 都会放弃索引）");
    }

    static void compositeIndexDemo(Connection conn) throws SQLException {
        System.out.println("\n== 复合索引 (status, amount) 最左前缀 ==");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX idx_status_amount ON orders(status, amount)");
        }
        String both = "SELECT COUNT(*) FROM orders WHERE status = 'PAID' AND amount = 500";
        String leftOnly = "SELECT COUNT(*) FROM orders WHERE status = 'NEW'";
        String rightOnly = "SELECT COUNT(*) FROM orders WHERE amount = 500"; // 跳过最左列 -> 无法按索引定位
        System.out.printf("  status + amount  %.2f ms  <- 两列都能定位%n", timeQuery(conn, both) / 1e6);
        System.out.printf("  仅 status        %.2f ms  <- 最左列可定位（命中 90%% 行，所以本身就慢）%n", timeQuery(conn, leftOnly) / 1e6);
        System.out.printf("  仅 amount        %.2f ms  <- 跳过最左列，索引按 status 排序，amount 无序，只能全扫%n", timeQuery(conn, rightOnly) / 1e6);
        System.out.println("  类比电话簿按(姓, 名)排序：知道姓能快速翻到，只知道名只能一页页找");
        System.out.println("  覆盖索引：SELECT status, amount ... 只需读索引不回表（MySQL EXPLAIN Extra: Using index）");
    }

    static void isolationDemo() throws Exception {
        System.out.println("\n== 隔离级别：READ COMMITTED 下的不可重复读 ==");
        try (Connection c1 = DriverManager.getConnection(URL, "sa", "");
             Connection c2 = DriverManager.getConnection(URL, "sa", "")) {
            c1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c1.setAutoCommit(false);
            System.out.println("  事务1 第一次读 amount(id=1): " + readAmount(c1, 1));
            try (Statement st = c2.createStatement()) {
                st.executeUpdate("UPDATE orders SET amount = 999 WHERE id = 1"); // 事务2 自动提交
            }
            System.out.println("  事务2 已提交修改为 999");
            System.out.println("  事务1 第二次读 amount(id=1): " + readAmount(c1, 1) + "  <- 不可重复读");
            c1.commit();
            System.out.println("  MySQL InnoDB 默认 REPEATABLE READ：第二次仍读到旧值（MVCC 快照在首次 SELECT 时建立）");
        }
    }

    static void rowLockDemo() throws Exception {
        System.out.println("\n== 行锁：事务1 持有 id=2 的写锁，事务2 更新同一行需等待 ==");
        try (Connection c1 = DriverManager.getConnection(URL, "sa", "")) {
            c1.setAutoCommit(false);
            try (Statement st = c1.createStatement()) {
                st.executeUpdate("UPDATE orders SET amount = 1 WHERE id = 2"); // 持锁不提交
            }
            CountDownLatch done = new CountDownLatch(1);
            long[] waited = new long[1];
            Thread t2 = new Thread(() -> {
                try (Connection c2 = DriverManager.getConnection(URL, "sa", "")) {
                    long s = System.nanoTime();
                    try (Statement st = c2.createStatement()) {
                        st.executeUpdate("UPDATE orders SET amount = 2 WHERE id = 2");
                    }
                    waited[0] = System.nanoTime() - s;
                } catch (SQLException e) {
                    System.out.println("  事务2 异常: " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
            t2.start();
            Thread.sleep(500);
            System.out.println("  事务1 睡了 500ms 后提交");
            c1.commit();
            done.await();
            System.out.printf("  事务2 阻塞了约 %.0f ms 才拿到锁%n", waited[0] / 1e6);
            try (Statement st = c1.createStatement()) {
                st.executeUpdate("UPDATE orders SET amount = 3 WHERE id = 3"); // 另一行不冲突
                System.out.println("  更新不同行 id=3 不受影响（InnoDB 行锁加在索引记录上；无索引条件会退化为锁全表）");
            }
            c1.commit();
        }
    }

    static void bPlusTreeIdea() {
        System.out.println("\n== B+ 树的思想：有序 + 叶子链表 => 等值与范围都快 ==");
        TreeMap<Integer, String> index = new TreeMap<>(); // 红黑树模拟"有序索引"
        for (int i = 0; i < 100; i += 7) {
            index.put(i, "row#" + i);
        }
        System.out.println("  等值 42 -> " + index.get(42) + "，范围 [20,50) -> " + index.subMap(20, 50).keySet());
        System.out.println("  B+ 树相对红黑树：节点多叉（一个 16KB 页存上千个键），高度 3 即可容纳千万行，磁盘 IO 次数 = 树高");
        System.out.println("  叶子节点存数据（聚簇索引）或主键值（二级索引 -> 回表），叶子间双向链表方便范围扫描");
    }

    static void printSummary() {
        System.out.println("""

                索引：
                  - InnoDB 聚簇索引按主键组织数据，二级索引叶子存主键 -> 主键应短且递增（自增 / 雪花 ID），UUID 会导致页分裂
                  - 建索引原则：区分度高的列、WHERE/ORDER BY/JOIN 列、复合索引把等值列放前面、范围列放最后；避免过多索引拖慢写
                  - EXPLAIN 看 type（const > eq_ref > ref > range > index > ALL）、key、rows、Extra（Using index / Using filesort / Using temporary）
                事务 ACID 与实现：
                  - 原子性 undo log、持久性 redo log（WAL，两阶段提交保证与 binlog 一致）、隔离性 锁 + MVCC、一致性是目标
                  - 隔离级别：读未提交 < 读已提交 < 可重复读（InnoDB 默认，用间隙锁基本解决幻读）< 串行化
                  - MVCC：每行隐藏 trx_id、roll_pointer，ReadView 判断版本可见性
                锁：
                  - 共享锁 S / 排他锁 X；意向锁 IS/IX 表级；记录锁、间隙锁、临键锁（Next-Key）
                  - 死锁：InnoDB 自动检测并回滚代价小的事务；避免：按固定顺序加锁、缩短事务、合理索引
                调优路径：慢查询日志 -> EXPLAIN -> 加/改索引 -> 改写 SQL（避免 SELECT *、深分页用游标 id > ? LIMIT n）-> 拆表 / 读写分离 / 缓存
                """);
    }

    private static void explain(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("EXPLAIN " + sql)) {
            List<String> plan = new ArrayList<>();
            while (rs.next()) {
                plan.add(rs.getString(1).replaceAll("\\s+", " "));
            }
            String p = String.join(" ", plan);
            int idx = p.indexOf("/*");
            String scan = idx >= 0 ? p.substring(idx) : p;
            System.out.println("  EXPLAIN " + sql.replace("SELECT COUNT(*) FROM orders ", "") + "\n      -> " + scan);
        }
    }

    private static long timeQuery(Connection conn, String sql) throws SQLException {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long s = System.nanoTime();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                rs.next();
            }
            best = Math.min(best, System.nanoTime() - s);
        }
        return best;
    }

    private static int readAmount(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT amount FROM orders WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
