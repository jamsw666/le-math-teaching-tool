package com.javalearning.middleware;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 8-6 分库分表（ShardingSphere）。
 *
 * <p>演示：取模分片与 SQL 改写（ShardingSphere-JDBC 的核心工作）、按 user_id 分片后"按订单号查"要全路由的问题、
 * 一致性哈希 vs 取模在扩容时的数据迁移量差异、雪花算法分布式 ID（分表后不能用自增）。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.middleware.ShardingDemo
 */
public class ShardingDemo {

    public static void main(String[] args) throws Exception {
        sqlRewrite();
        rangeQueryProblem();
        consistentHashing();
        snowflake();
        printSummary();
    }

    // ---------------- 分片路由 + SQL 改写 ----------------
    static final int DB_COUNT = 2;
    static final int TABLE_COUNT = 4;

    /** 模拟 ShardingSphere：解析逻辑 SQL -> 提取分片键 -> 计算真实库表 -> 改写 SQL。 */
    static Long extractShardingValue(String sql) {
        Matcher where = Pattern.compile("user_id\\s*=\\s*(\\d+)").matcher(sql);
        if (where.find()) {
            return Long.parseLong(where.group(1));
        }
        // INSERT INTO t(col1, col2, ...) VALUES (v1, v2, ...)：按列名位置找到 user_id 对应的值
        Matcher insert = Pattern.compile("INSERT INTO \\w+\\(([^)]*)\\)\\s*VALUES\\s*\\(([^)]*)\\)").matcher(sql);
        if (insert.find()) {
            String[] cols = insert.group(1).split("\\s*,\\s*");
            String[] vals = insert.group(2).split("\\s*,\\s*");
            for (int i = 0; i < cols.length; i++) {
                if (cols[i].trim().equalsIgnoreCase("user_id")) {
                    return Long.parseLong(vals[i].trim());
                }
            }
        }
        return null;
    }

    static List<String> route(String logicSql) {
        Long shardingValue = extractShardingValue(logicSql);
        List<String> real = new ArrayList<>();
        if (shardingValue != null) {
            long userId = shardingValue;
            int db = (int) (userId % DB_COUNT);
            int table = (int) (userId % TABLE_COUNT);
            real.add("ds_" + db + " :: " + logicSql.replace("t_order", "t_order_" + table));
        } else {
            // 无分片键 -> 广播到所有真实表，再在内存归并结果
            for (int db = 0; db < DB_COUNT; db++) {
                for (int t = 0; t < TABLE_COUNT; t++) {
                    if (t % DB_COUNT == db) real.add("ds_" + db + " :: " + logicSql.replace("t_order", "t_order_" + t));
                }
            }
        }
        return real;
    }

    static void sqlRewrite() {
        System.out.println("== 分片路由与 SQL 改写（2 库 x 4 表，分片键 user_id） ==");
        for (String sql : List.of(
                "INSERT INTO t_order(order_id, user_id, amount) VALUES (1001, 7, 99)",
                "SELECT * FROM t_order WHERE user_id = 7 AND status = 'PAID'",
                "SELECT * FROM t_order WHERE order_id = 1001")) {
            System.out.println("  逻辑 SQL: " + sql);
            route(sql).forEach(r -> System.out.println("      -> " + r));
        }
        System.out.println("  没带分片键的查询会全路由 + 内存归并（ORDER BY / LIMIT / COUNT 都要在中间件层重新计算），性能最差");
    }

    static void rangeQueryProblem() {
        System.out.println("\n== 非分片键查询的解决方案 ==");
        System.out.println("  1. 基因法：order_id 低位嵌入 user_id 的分片位 -> 用 order_id 也能算出分片");
        long userId = 7;
        long seq = 123456;
        int bits = 2; // 4 张表需要 2 位
        long orderId = (seq << bits) | (userId % TABLE_COUNT);
        System.out.printf("     user_id=%d, seq=%d -> order_id=%d, 从 order_id 低 %d 位反推表=%d%n", userId, seq, orderId, bits, orderId & (TABLE_COUNT - 1));
        System.out.println("  2. 映射表 / 索引表：order_id -> user_id 的小表（可放 Redis）");
        System.out.println("  3. 异构索引：数据同步到 ES，复杂查询走 ES，主键回查 MySQL");
        System.out.println("  4. 冗余双写：按 user_id 和按 merchant_id 各存一份（买家库 / 卖家库）");
    }

    // ---------------- 一致性哈希 ----------------
    static class ConsistentHash {
        private final SortedMap<Long, String> ring = new TreeMap<>();
        private final int virtualNodes;

        ConsistentHash(List<String> nodes, int virtualNodes) {
            this.virtualNodes = virtualNodes;
            nodes.forEach(this::addNode);
        }

        void addNode(String node) {
            for (int i = 0; i < virtualNodes; i++) ring.put(hash(node + "#" + i), node);
        }

        String locate(String key) {
            if (ring.isEmpty()) return null;
            SortedMap<Long, String> tail = ring.tailMap(hash(key));
            return tail.isEmpty() ? ring.get(ring.firstKey()) : tail.get(tail.firstKey()); // 顺时针第一个节点
        }

        static long hash(String s) {
            try {
                byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
                return ((long) (d[3] & 0xFF) << 24) | ((d[2] & 0xFF) << 16) | ((d[1] & 0xFF) << 8) | (d[0] & 0xFF);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static void consistentHashing() {
        System.out.println("\n== 扩容 3 -> 4 节点：取模 vs 一致性哈希 的数据迁移量（10000 个 key） ==");
        int n = 10_000;
        int movedMod = 0;
        for (int k = 0; k < n; k++) {
            if (k % 3 != k % 4) movedMod++;
        }
        List<String> nodes = new ArrayList<>(List.of("node-A", "node-B", "node-C"));
        ConsistentHash before = new ConsistentHash(nodes, 150);
        nodes.add("node-D");
        ConsistentHash after = new ConsistentHash(nodes, 150);
        int movedCh = 0;
        Map<String, Integer> dist = new TreeMap<>();
        for (int k = 0; k < n; k++) {
            String key = "key-" + k;
            if (!before.locate(key).equals(after.locate(key))) movedCh++;
            dist.merge(after.locate(key), 1, Integer::sum);
        }
        System.out.printf("  取模 hash%%N：迁移 %.1f%% 的数据%n", movedMod * 100.0 / n);
        System.out.printf("  一致性哈希(150 虚拟节点)：迁移 %.1f%%（理论 1/4=25%%），新分布 %s%n", movedCh * 100.0 / n, dist);
        System.out.println("  虚拟节点解决物理节点少时分布不均的问题；Redis Cluster 用固定 16384 槽是另一种思路");
    }

    // ---------------- 雪花算法 ----------------
    /** 64 位：1 符号 | 41 时间戳(ms) | 5 数据中心 | 5 机器 | 12 序列号。趋势递增、无中心、每毫秒每机 4096 个。 */
    static class Snowflake {
        private static final long EPOCH = 1_700_000_000_000L;
        private final long workerId;
        private long lastTs = -1;
        private long seq = 0;
        final AtomicLong clockBackCount = new AtomicLong();

        Snowflake(long workerId) { this.workerId = workerId; }

        synchronized long nextId() {
            long ts = System.currentTimeMillis();
            if (ts < lastTs) {
                clockBackCount.incrementAndGet();
                throw new IllegalStateException("时钟回拨 " + (lastTs - ts) + "ms，拒绝生成 ID");
            }
            if (ts == lastTs) {
                seq = (seq + 1) & 0xFFF;
                if (seq == 0) {
                    while (ts <= lastTs) ts = System.currentTimeMillis(); // 本毫秒用尽，自旋到下一毫秒
                }
            } else {
                seq = 0;
            }
            lastTs = ts;
            return ((ts - EPOCH) << 22) | (workerId << 12) | seq;
        }
    }

    static void snowflake() {
        System.out.println("\n== 雪花算法分布式 ID ==");
        Snowflake sf = new Snowflake(5);
        long prev = 0;
        boolean increasing = true;
        for (int i = 0; i < 5; i++) {
            long id = sf.nextId();
            System.out.printf("  %d  (二进制 %s)%n", id, Long.toBinaryString(id));
            increasing &= id > prev;
            prev = id;
        }
        System.out.println("  单调递增: " + increasing + "，可解析出时间戳: " + ((prev >> 22) + 1_700_000_000_000L));
        System.out.println("  为什么不用 UUID：无序导致 InnoDB 页分裂、36 字符太长；分表后不能用自增 ID（各表重复），可用号段模式（Leaf）/ 雪花");
    }

    static void printSummary() {
        System.out.println("""

                何时分：单表 > 1000 万~2000 万行或 > 几十 GB 且索引优化、读写分离、归档都不够时；先垂直拆（按业务拆库/冷热列拆表）再水平拆
                分片键选择：查询最常用的维度（用户维度），分布均匀，不可变
                分片算法：取模（均匀但扩容难，预先分 1024 表再按需迁移）、范围/时间（易热点但便于归档）、一致性哈希、基因法
                带来的问题：
                  - 跨分片 JOIN -> 冗余字段 / 应用层聚合 / 不跨分片设计（绑定表）
                  - 分布式事务 -> 尽量单分片事务；Seata AT / 柔性事务 / 最终一致
                  - 分布式 ID、跨分片排序分页（深分页代价巨大，用游标）
                  - 扩容迁移：双写 + 全量迁移 + 增量追平 + 校验 + 切流
                ShardingSphere：JDBC（嵌入式驱动，无中心）与 Proxy（独立进程，透明于语言）；也提供读写分离、数据加密、影子库
                参考：apache/shardingsphere、Meituan Leaf（分布式 ID）、《大型网站技术架构》
                """);
    }
}
