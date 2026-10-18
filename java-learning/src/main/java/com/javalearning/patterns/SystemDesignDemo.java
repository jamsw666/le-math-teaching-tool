package com.javalearning.patterns;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 10-8 系统设计与性能优化。
 *
 * <p>以“短链服务”为例走一遍系统设计流程：需求澄清 -> 容量估算 -> 核心算法（Base62）-> 存储与缓存 ->
 * 用 Zipf 分布流量实测“10% 容量的缓存命中 80%+ 请求” -> 串行 vs 并行聚合的延迟对比 ->
 * 利特尔法则估算线程池/实例数 -> 性能优化方法论。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.patterns.SystemDesignDemo
 */
public class SystemDesignDemo {

    public static void main(String[] args) throws Exception {
        capacityEstimation();
        base62();
        cacheHitRateWithZipf();
        fanOutLatency();
        littlesLaw();
        printMethodology();
    }

    // ---------------- 容量估算 ----------------
    static void capacityEstimation() {
        System.out.println("== 1. 需求与容量估算（短链服务，面试中先做这一步） ==");
        long dau = 10_000_000L;
        double writesPerUserPerDay = 0.1;
        int readWriteRatio = 100;
        long writesPerDay = (long) (dau * writesPerUserPerDay);
        long readsPerDay = writesPerDay * readWriteRatio;
        long secondsPerDay = 86_400;
        long writeQps = writesPerDay / secondsPerDay;
        long readQps = readsPerDay / secondsPerDay;
        long peakFactor = 3;
        int bytesPerRecord = 500; // 短码 + 长链 + 用户 + 时间 + 索引开销
        long storage5y = writesPerDay * 365 * 5 * bytesPerRecord;
        System.out.printf("  DAU %,d，每人每天创建 %.1f 条 -> 写 %,d 条/天 ≈ %d QPS，峰值 x%d ≈ %d QPS%n",
                dau, writesPerUserPerDay, writesPerDay, writeQps, peakFactor, writeQps * peakFactor);
        System.out.printf("  读写比 %d:1 -> 读 ≈ %,d QPS，峰值 ≈ %,d QPS（读多写少 -> 缓存是核心）%n", readWriteRatio, readQps, readQps * peakFactor);
        System.out.printf("  5 年存储 ≈ %,d 条 x %d B ≈ %.1f TB（单 MySQL 可承受，但建议按短码分表或用 KV 存储）%n",
                writesPerDay * 365 * 5, bytesPerRecord, storage5y / 1e12);
        System.out.printf("  带宽：读 %,d QPS x 500 B ≈ %.1f MB/s（301/302 跳转响应很小，带宽不是瓶颈）%n", readQps, readQps * 500 / 1e6);
        System.out.println("  结论驱动设计：读峰值数千 QPS -> 多级缓存 + 无状态横向扩展；写仅数十 QPS -> 单主 DB 足够；ID 生成需分布式唯一且短");
    }

    // ---------------- Base62 ----------------
    static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    static String encode(long id) {
        if (id == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while (id > 0) { sb.append(ALPHABET.charAt((int) (id % 62))); id /= 62; }
        return sb.reverse().toString();
    }

    static long decode(String s) {
        long id = 0;
        for (char c : s.toCharArray()) id = id * 62 + ALPHABET.indexOf(c);
        return id;
    }

    static void base62() {
        System.out.println("\n== 2. 核心算法：全局自增 ID -> Base62 短码 ==");
        for (long id : new long[]{1, 62, 1_000_000, 56_800_235_583L}) {
            String code = encode(id);
            System.out.printf("  id %,15d -> %-8s -> 解码 %,d%n", id, code, decode(code));
        }
        System.out.printf("  6 位 Base62 = 62^6 ≈ %,d 个短码，7 位 ≈ %,d，够用几十年%n", (long) Math.pow(62, 6), (long) Math.pow(62, 7));
        System.out.println("  ID 来源：号段模式（DB 每次取 1000 个，内存分配）/ 雪花算法（见 ShardingDemo）；连续 ID 可被遍历 -> 可加随机偏移或混淆");
        System.out.println("  另一方案：对长链做 MD5 取前 7 位，冲突时加盐重算；优点无状态，缺点需查重");
    }

    // ---------------- 缓存命中率 ----------------
    /** 简单 LRU。 */
    static class Lru<K, V> extends LinkedHashMap<K, V> {
        private final int cap;
        Lru(int cap) { super(16, 0.75f, true); this.cap = cap; }
        @Override protected boolean removeEldestEntry(Map.Entry<K, V> e) { return size() > cap; }
    }

    /** Zipf 分布：第 k 热的 key 被访问概率 ∝ 1/k^s。互联网流量普遍长尾。 */
    static int[] zipfSamples(int n, int keys, double s, Random rnd) {
        double[] cdf = new double[keys];
        double sum = 0;
        for (int k = 1; k <= keys; k++) { sum += 1.0 / Math.pow(k, s); cdf[k - 1] = sum; }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            double r = rnd.nextDouble() * sum;
            int lo = 0;
            int hi = keys - 1;
            while (lo < hi) { int mid = (lo + hi) >>> 1; if (cdf[mid] < r) lo = mid + 1; else hi = mid; }
            out[i] = lo;
        }
        return out;
    }

    static void cacheHitRateWithZipf() {
        System.out.println("\n== 3. 缓存设计：Zipf 流量下，小缓存就有高命中率 ==");
        int keys = 100_000;
        int requests = 500_000;
        Random rnd = new Random(42);
        int[] zipf = zipfSamples(requests, keys, 1.0, rnd);
        int[] uniform = new int[requests];
        for (int i = 0; i < requests; i++) uniform[i] = rnd.nextInt(keys);
        for (double ratio : new double[]{0.01, 0.05, 0.10, 0.20}) {
            int cap = (int) (keys * ratio);
            System.out.printf("  缓存容量 = %3.0f%% 的 key：Zipf 流量命中率 %5.1f%%   均匀流量命中率 %5.1f%%%n",
                    ratio * 100, hitRate(zipf, cap) * 100, hitRate(uniform, cap) * 100);
        }
        System.out.println("  真实流量是长尾的，所以 Redis 只需缓存热点就能挡住绝大多数读；均匀流量下缓存几乎无用 -> 先看访问分布再决定要不要缓存");
        System.out.println("  短链读路径：本地 Caffeine（毫秒内热点）-> Redis -> DB；不存在的短码用布隆过滤器挡穿透（见 CacheConsistencyDemo）");
    }

    static double hitRate(int[] trace, int cap) {
        Lru<Integer, Boolean> cache = new Lru<>(cap);
        int hit = 0;
        for (int k : trace) {
            if (cache.get(k) != null) hit++; else cache.put(k, Boolean.TRUE);
        }
        return (double) hit / trace.length;
    }

    // ---------------- 并行聚合 ----------------
    static void fanOutLatency() throws Exception {
        System.out.println("\n== 4. 延迟优化：聚合页串行调用 vs 并行 fan-out ==");
        Supplier<String> user = () -> slow(60, "用户信息");
        Supplier<String> orders = () -> slow(90, "订单列表");
        Supplier<String> coupons = () -> slow(70, "优惠券");
        long t0 = System.currentTimeMillis();
        List<String> serial = List.of(user.get(), orders.get(), coupons.get());
        long serialMs = System.currentTimeMillis() - t0;

        ExecutorService pool = Executors.newFixedThreadPool(3);
        t0 = System.currentTimeMillis();
        var f1 = CompletableFuture.supplyAsync(user, pool);
        var f2 = CompletableFuture.supplyAsync(orders, pool);
        var f3 = CompletableFuture.supplyAsync(coupons, pool).completeOnTimeout("优惠券(降级:超时)", 200, java.util.concurrent.TimeUnit.MILLISECONDS);
        List<String> parallel = List.of(f1.join(), f2.join(), f3.join());
        long parallelMs = System.currentTimeMillis() - t0;
        pool.shutdown();
        System.out.printf("  串行 %d ms %s%n  并行 %d ms %s（延迟 = 最慢的一个，而非总和；非核心数据配超时降级）%n", serialMs, serial, parallelMs, parallel);
        System.out.println("  其他延迟手段：批量接口（N 次 RPC -> 1 次）、数据预聚合/宽表、异步化（下单后发 MQ 做积分/通知）、就近缓存、连接复用（HTTP keep-alive / 连接池）");
    }

    static String slow(long ms, String what) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return what;
    }

    // ---------------- 利特尔法则 ----------------
    static void littlesLaw() {
        System.out.println("\n== 5. 容量规划：利特尔法则  并发数 L = 吞吐 λ x 平均耗时 W ==");
        double targetQps = 3000;
        double avgLatencySec = 0.05;
        double concurrency = targetQps * avgLatencySec;
        int threadsPerInstance = 200; // Tomcat 默认 maxThreads
        double utilizationTarget = 0.6;
        int instances = (int) Math.ceil(concurrency / (threadsPerInstance * utilizationTarget));
        System.out.printf("  目标 %.0f QPS x 平均 %.0f ms = 同时在处理 %.0f 个请求%n", targetQps, avgLatencySec * 1000, concurrency);
        System.out.printf("  每实例 %d 线程，按 %.0f%% 利用率 -> 需要 %d 个实例（再乘峰值系数与冗余 N+1）%n", threadsPerInstance, utilizationTarget * 100, instances);
        System.out.println("  反过来：耗时从 50ms 优化到 10ms，同样 QPS 只需 1/5 的并发 -> 优化延迟就是扩容");
        System.out.println("  线程池：CPU 密集 ≈ 核数+1；IO 密集 ≈ 核数 x (1 + 等待时间/计算时间)；更好的办法是压测找拐点，或用虚拟线程消灭这个问题（见 VirtualThreadDemo）");
    }

    static void printMethodology() {
        System.out.println("""

                系统设计答题框架（45 分钟）：
                  1. 澄清需求（功能 / 非功能：QPS、延迟、一致性、可用性目标）  2. 容量估算  3. API 与数据模型
                  4. 高层架构图（客户端 -> CDN/网关 -> 服务 -> 缓存 -> DB/MQ）  5. 深入核心难点（ID 生成、热点、一致性）
                  6. 扩展性 / 可用性 / 容灾（多活、降级）  7. 监控与演进
                高可用手段：无状态 + 多副本 + 负载均衡；冗余（N+1）；故障隔离；限流熔断降级；多机房多活；混沌工程验证
                高性能手段：缓存（多级）、异步（MQ）、并行（fan-out）、批量、池化（连接/线程/对象）、读写分离、分库分表、索引、CDN、协议（HTTP/2、gRPC、Protobuf）
                性能优化方法论：
                  先测量再优化（Arthas / async-profiler 火焰图 / JFR / APM），找到真正的热点（通常 20% 代码占 80% 时间）
                  由外向内：架构（少调用）> 算法与数据结构 > IO（批量、缓存、异步）> JVM（GC、堆）> 代码微观（StringBuilder、避免装箱）
                  设定目标与预算（P99 < 200ms），每次只改一个变量，用压测验证，防止“优化”引入 bug
                  常见低垂果实：N+1 查询、缺索引、同步调外部接口、日志过多、大对象反复创建、锁粒度过大、线程池参数不当、序列化低效
                经典题：短链、Feed 流（推拉结合）、秒杀、IM、点赞计数、分布式 ID、限流器、附近的人（GeoHash）、搜索联想（Trie）、文件存储（分块 + 去重）
                参考：donnemartin/system-design-primer、《数据密集型应用系统设计》、《大型网站技术架构》、ByteByteGo（Alex Xu）系统设计系列、Google SRE Book
                """);
    }
}
