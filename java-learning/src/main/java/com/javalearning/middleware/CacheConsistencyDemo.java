package com.javalearning.middleware;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 8-7 分布式缓存一致性。
 *
 * <p>用两个 Map 模拟 DB 与 Cache，用线程交错复现经典不一致场景：
 * "先更新 DB 再更新缓存"的并发写覆盖、"先删缓存再更新 DB"的脏读回填、
 * Cache-Aside（先更新 DB 再删缓存）为什么基本够用，以及延迟双删、缓存击穿的互斥重建、布隆过滤器防穿透。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.middleware.CacheConsistencyDemo
 */
public class CacheConsistencyDemo {

    static final Map<String, Integer> db = new ConcurrentHashMap<>();
    static final Map<String, Integer> cache = new ConcurrentHashMap<>();
    static final AtomicInteger dbReads = new AtomicInteger();

    static int readDb(String k) {
        dbReads.incrementAndGet();
        sleep(20); // 模拟 DB 慢
        return db.getOrDefault(k, -1);
    }

    public static void main(String[] args) throws Exception {
        doubleWriteProblem();
        deleteThenUpdateProblem();
        cacheAside();
        delayedDoubleDelete();
        breakdownWithMutex();
        bloomFilterForPenetration();
        printSummary();
    }

    /** 方案一：更新 DB 后更新缓存。两个写请求交错 -> 缓存里是旧值。 */
    static void doubleWriteProblem() throws Exception {
        System.out.println("== 方案 A：先更新 DB 再更新缓存（并发写覆盖） ==");
        db.put("stock", 100);
        cache.put("stock", 100);
        CountDownLatch gate = new CountDownLatch(1);
        Thread w1 = new Thread(() -> {
            await(gate);
            db.put("stock", 1);
            sleep(50); // 线程 1 更新缓存前被调度走
            cache.put("stock", 1);
        });
        Thread w2 = new Thread(() -> {
            await(gate);
            sleep(10);
            db.put("stock", 2);
            cache.put("stock", 2);
        });
        w1.start(); w2.start(); gate.countDown(); w1.join(); w2.join();
        System.out.println("  写1: DB=1 (停顿) Cache=1；写2: DB=2 Cache=2 -> 最终 DB=" + db.get("stock") + " Cache=" + cache.get("stock") + "  <- 不一致，且会一直错下去");
        System.out.println("  另外：每次写都刷缓存浪费（写多读少时缓存可能从未被读），且缓存值若需复杂计算更浪费");
    }

    /** 方案二：先删缓存再更新 DB。读请求在中间回填旧值。 */
    static void deleteThenUpdateProblem() throws Exception {
        System.out.println("\n== 方案 B：先删缓存再更新 DB（读请求回填旧值） ==");
        db.put("price", 100);
        cache.put("price", 100);
        Thread writer = new Thread(() -> {
            cache.remove("price");
            sleep(60); // 更新 DB 慢
            db.put("price", 200);
        });
        Thread reader = new Thread(() -> {
            sleep(10);
            Integer v = cache.get("price");
            if (v == null) {
                v = readDb("price"); // 读到旧值 100
                cache.put("price", v);
            }
        });
        writer.start(); reader.start(); writer.join(); reader.join();
        System.out.println("  写: 删缓存 (更新 DB 慢) DB=200；读: 缓存 miss -> 读 DB 旧值 100 -> 回填 -> 最终 DB=" + db.get("price") + " Cache=" + cache.get("price") + "  <- 不一致");
    }

    /** 方案三 Cache-Aside：先更新 DB 再删缓存。理论上也有不一致窗口，但需要"读 DB 比写 DB 还慢"的罕见条件。 */
    static void cacheAside() throws Exception {
        System.out.println("\n== 方案 C：Cache-Aside（先更新 DB 再删缓存），业界默认 ==");
        db.put("name", 1);
        cache.remove("name");
        Thread reader = new Thread(() -> {
            Integer v = cache.get("name");
            if (v == null) {
                v = readDb("name"); // 20ms，读到 1
                sleep(50); // 极端：读到旧值后迟迟不回填
                cache.putIfAbsent("name", v);
            }
        });
        Thread writer = new Thread(() -> {
            sleep(5);
            db.put("name", 2);
            cache.remove("name");
        });
        reader.start(); writer.start(); reader.join(); writer.join();
        System.out.println("  读: miss -> 读 DB=1 (长时间停顿) ...；写: DB=2 删缓存；读: 回填 1 -> DB=" + db.get("name") + " Cache=" + cache.get("name"));
        System.out.println("  这个窗口要求：读在写之前读到旧值，却在写删缓存之后才回填 -> 写 DB 通常比读 DB 慢得多，概率极低，加缓存过期时间兜底即可");
        System.out.println("  删除失败怎么办：重试队列 / 订阅 binlog（canal）异步删缓存，与业务代码解耦");
    }

    static void delayedDoubleDelete() throws Exception {
        System.out.println("\n== 延迟双删：删缓存 -> 更新 DB -> 睡 N ms -> 再删缓存 ==");
        db.put("cfg", 1);
        cache.put("cfg", 1);
        Thread writer = new Thread(() -> {
            cache.remove("cfg");
            db.put("cfg", 2);
            sleep(80); // N 取"一次读请求的耗时"量级
            cache.remove("cfg"); // 把期间被回填的旧值删掉
        });
        Thread reader = new Thread(() -> {
            sleep(5);
            if (cache.get("cfg") == null) cache.put("cfg", readDb("cfg"));
        });
        writer.start(); reader.start(); writer.join(); reader.join();
        System.out.println("  第二次删除后 Cache=" + cache.get("cfg") + "，下一次读会加载 DB=" + db.get("cfg") + "。缺点：N 难以确定，写延迟增加");
    }

    /** 缓存击穿：热点 key 失效瞬间大量请求打到 DB -> 用互斥锁让一个线程重建，其他等待。 */
    static void breakdownWithMutex() throws Exception {
        System.out.println("\n== 缓存击穿：互斥重建 ==");
        db.put("hot", 42);
        cache.remove("hot");
        dbReads.set(0);
        ReentrantLock rebuildLock = new ReentrantLock();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        for (int i = 0; i < 20; i++) {
            pool.submit(() -> {
                Integer v = cache.get("hot");
                if (v != null) return;
                if (rebuildLock.tryLock()) { // 分布式环境换成 Redis SETNX
                    try {
                        if (cache.get("hot") == null) cache.put("hot", readDb("hot")); // double check
                    } finally {
                        rebuildLock.unlock();
                    }
                } else {
                    while (cache.get("hot") == null) sleep(5); // 其他线程等待重建完成
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  20 个并发请求，DB 只被读了 " + dbReads.get() + " 次。另一种方案：逻辑过期（缓存永不过期，值里带过期时间，异步刷新）");
    }

    /** 布隆过滤器：用 k 个哈希把 key 映射到位数组。说"不存在"一定不存在，说"存在"可能误判。 */
    static class BloomFilter {
        private final long[] bits;
        private final int size;
        private final int hashes;

        BloomFilter(int size, int hashes) {
            this.size = size;
            this.hashes = hashes;
            this.bits = new long[(size + 63) / 64];
        }

        void add(String key) {
            for (int i = 0; i < hashes; i++) {
                int idx = index(key, i);
                bits[idx >> 6] |= 1L << (idx & 63);
            }
        }

        boolean mightContain(String key) {
            for (int i = 0; i < hashes; i++) {
                int idx = index(key, i);
                if ((bits[idx >> 6] & (1L << (idx & 63))) == 0) return false;
            }
            return true;
        }

        private int index(String key, int i) {
            int h = key.hashCode();
            int h2 = h >>> 16 | h << 16;
            return Math.floorMod(h + i * h2 + i * i, size); // 双重哈希模拟 k 个哈希函数
        }
    }

    static void bloomFilterForPenetration() {
        System.out.println("\n== 缓存穿透：布隆过滤器 ==");
        BloomFilter bf = new BloomFilter(100_000, 4);
        for (int i = 0; i < 10_000; i++) bf.add("user:" + i);
        int falsePositive = 0;
        for (int i = 10_000; i < 20_000; i++) {
            if (bf.mightContain("user:" + i)) falsePositive++;
        }
        System.out.printf("  10000 个存在的 id 全部放行: %b；10000 个不存在的 id 误判 %d 个（%.2f%%）%n",
                bf.mightContain("user:123"), falsePositive, falsePositive / 100.0);
        System.out.println("  1 万元素用 10 万 bit ≈ 12KB，误判率可通过加大位数组 / 调整哈希个数降低；不支持删除（计数布隆 / 布谷鸟过滤器可）");
        System.out.println("  另一方案：缓存空值（设短过期），简单但大量随机 key 攻击时会占内存");
    }

    static void printSummary() {
        System.out.println("""

                一致性结论：
                  - 缓存与 DB 不可能强一致（除非加分布式锁串行化所有读写，失去缓存意义），目标是"最终一致 + 不一致窗口尽量短"
                  - 推荐：Cache-Aside（读：先缓存后 DB 回填；写：先 DB 后删缓存）+ 过期时间兜底 + binlog 异步删除补偿
                  - 更新而非删除缓存的场景：读极多、计算成本低且能接受并发写覆盖（或用版本号 / CAS 比较时间戳）
                其他模式：
                  Read/Write Through（缓存层代理 DB 读写，应用只见缓存，如 Guava LoadingCache / JetCache）
                  Write Behind（先写缓存异步批量刷 DB，吞吐高但可能丢数据，如 InnoDB buffer pool 思想）
                多级缓存：本地缓存（Caffeine，纳秒级，容量小，多实例不一致 -> 用 MQ 广播失效）+ Redis + DB
                热点 key：本地缓存 + 随机后缀打散到多个 Redis 节点 + 读写分离；大 key：拆分 / 压缩 / 异步删除 UNLINK
                参考：ben-manes/caffeine、alibaba/jetcache、alibaba/canal、《凤凰架构》缓存章节
                """);
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void await(CountDownLatch l) {
        try {
            l.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
