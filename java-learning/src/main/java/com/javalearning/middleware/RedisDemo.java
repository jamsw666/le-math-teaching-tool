package com.javalearning.middleware;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 8-3 Redis：数据结构 / 缓存 / 持久化。
 *
 * <p>不依赖真实 Redis，用 Java 实现一个单线程命令执行的 MiniRedis：
 * String / Hash / List / Set / ZSet 五种结构、过期（惰性 + 定期删除）、LRU 淘汰、
 * SETNX 分布式锁、以及 AOF 日志重放（持久化思想）。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.middleware.RedisDemo
 */
public class RedisDemo {

    public static void main(String[] args) throws Exception {
        MiniRedis redis = new MiniRedis(5);
        dataStructures(redis);
        expireDemo(redis);
        evictionDemo();
        distributedLockDemo();
        persistenceDemo(redis);
        singleThreadDemo();
        printSummary();
    }

    static void dataStructures(MiniRedis r) {
        System.out.println("== 五种数据结构 ==");
        r.set("user:1:name", "alice");
        r.incr("page:view");
        r.incr("page:view");
        System.out.println("  String  GET user:1:name=" + r.get("user:1:name") + "  INCR page:view=" + r.get("page:view") + "  (计数器 / 缓存对象 JSON / 分布式锁)");

        r.hset("user:1", "age", "30");
        r.hset("user:1", "city", "Shanghai");
        System.out.println("  Hash    HGETALL user:1=" + r.hgetall("user:1") + "  (对象字段级更新，比整体 JSON 省带宽)");

        r.lpush("queue:task", "t1");
        r.lpush("queue:task", "t2");
        r.rpush("queue:task", "t0");
        System.out.println("  List    LRANGE queue:task=" + r.lrange("queue:task") + "  RPOP=" + r.rpop("queue:task") + "  (简单队列 / 最新 N 条动态)");

        r.sadd("tags:post1", "java");
        r.sadd("tags:post1", "redis");
        r.sadd("tags:post2", "redis");
        r.sadd("tags:post2", "mysql");
        System.out.println("  Set     SINTER tags:post1 tags:post2=" + r.sinter("tags:post1", "tags:post2") + "  (去重、共同好友、抽奖)");

        r.zadd("rank", 95, "alice");
        r.zadd("rank", 88, "bob");
        r.zadd("rank", 99, "carol");
        r.zadd("rank", 91, "alice"); // 更新分数
        System.out.println("  ZSet    ZREVRANGE rank 0 -1 WITHSCORES=" + r.zrevrange("rank") + "  (排行榜 / 延迟队列：score=执行时间戳)");
        System.out.println("  底层编码：String=SDS(int/embstr/raw)  Hash=listpack/hashtable  List=quicklist  Set=intset/listpack/hashtable  ZSet=listpack/skiplist+dict");
    }

    static void expireDemo(MiniRedis r) throws InterruptedException {
        System.out.println("\n== 过期：惰性删除 + 定期删除 ==");
        r.set("session:abc", "token", 100);
        System.out.println("  SET session:abc EX 100ms -> GET=" + r.get("session:abc") + " TTL=" + r.ttl("session:abc") + "ms");
        Thread.sleep(150);
        System.out.println("  150ms 后 GET=" + r.get("session:abc") + "（惰性删除：访问时发现过期才删）");
        r.set("tmp:1", "x", 10);
        r.set("tmp:2", "y", 10);
        Thread.sleep(30);
        int removed = r.activeExpireCycle();
        System.out.println("  定期删除周期清理了 " + removed + " 个过期键（Redis 默认每秒 10 次随机抽样 20 个键）");
    }

    static void evictionDemo() {
        System.out.println("\n== 内存淘汰：maxmemory=3 个键 + allkeys-lru ==");
        MiniRedis small = new MiniRedis(3);
        small.set("a", "1");
        small.set("b", "2");
        small.set("c", "3");
        small.get("a"); // a 变为最近使用
        small.set("d", "4"); // 触发淘汰，b 最久未使用
        System.out.println("  依次 SET a b c，GET a，SET d -> 剩余键 " + small.keys() + "（b 被淘汰）");
        System.out.println("  Redis 的 LRU 是近似算法（随机采样 5 个取最久的），4.0+ 还有 LFU（按访问频率）");
    }

    static void distributedLockDemo() throws Exception {
        System.out.println("\n== SET NX PX 分布式锁 ==");
        MiniRedis r = new MiniRedis(100);
        AtomicInteger counter = new AtomicInteger();
        AtomicInteger lockFailed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 8; i++) {
            String owner = "client-" + i;
            pool.submit(() -> {
                for (int j = 0; j < 50; j++) {
                    if (r.setNx("lock:order:1", owner, 1000)) { // 值 = 唯一标识，防止误删他人锁
                        try {
                            counter.incrementAndGet();
                        } finally {
                            r.delIfValueMatches("lock:order:1", owner); // 真实场景用 Lua 保证 GET+DEL 原子
                        }
                    } else {
                        lockFailed.incrementAndGet();
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("  8 个客户端各尝试 50 次，成功加锁执行 " + counter.get() + " 次，竞争失败 " + lockFailed.get() + " 次");
        System.out.println("  锁必须：原子 SET NX PX（防死锁）+ 唯一 value + Lua 释放 + 看门狗续期（Redisson）；主从切换可能丢锁 -> RedLock 争议");
    }

    static void persistenceDemo(MiniRedis r) {
        System.out.println("\n== 持久化：AOF 重放恢复 ==");
        System.out.println("  AOF 日志条数: " + r.aof.size() + "，前 3 条: " + r.aof.subList(0, Math.min(3, r.aof.size())));
        MiniRedis recovered = new MiniRedis(100);
        recovered.replay(r.aof);
        System.out.println("  重放后 HGETALL user:1=" + recovered.hgetall("user:1") + "  ZSet rank=" + recovered.zrevrange("rank"));
        System.out.println("  RDB：fork 子进程写内存快照（COW），文件小恢复快，可能丢最后几分钟数据");
        System.out.println("  AOF：追加写命令，appendfsync always/everysec/no；文件大 -> AOF 重写；Redis 7 混合持久化 = RDB 头 + AOF 尾");
    }

    static void singleThreadDemo() {
        System.out.println("\n== 为什么单线程还快 ==");
        System.out.println("  纯内存操作 + IO 多路复用（epoll）+ 单线程无锁无上下文切换 + 高效数据结构；瓶颈在网络和内存而非 CPU");
        System.out.println("  6.0 引入多线程只处理网络读写与协议解析，命令执行仍是单线程 -> 所有命令天然原子（INCR/SETNX 可做锁与计数）");
        System.out.println("  因此要避免慢命令：KEYS * / 大 key 的 HGETALL / SMEMBERS，改用 SCAN / HSCAN / 拆分 key");
    }

    static void printSummary() {
        System.out.println("""

                缓存三大问题：
                  穿透（查不存在的数据）-> 缓存空值 / 布隆过滤器
                  击穿（热点 key 过期瞬间）-> 互斥锁重建 / 逻辑过期永不失效
                  雪崩（大量 key 同时过期或 Redis 宕机）-> 过期时间加随机、多级缓存、集群高可用、限流降级
                高可用：主从复制（异步）-> 哨兵 Sentinel（自动故障转移）-> Cluster（16384 个哈希槽分片，Gossip 协议）
                其他：Pipeline 批量减少 RTT；事务 MULTI/EXEC 无回滚；Lua 脚本原子；Pub/Sub 与 Stream（可持久化消费组）；
                      HyperLogLog（UV 估算）、Bitmap（签到）、GEO（附近的人）
                参考：Redis 设计与实现（黄健宏）、redis/redis、redisson/redisson、lettuce-io/lettuce-core
                """);
    }

    // ---------------- MiniRedis ----------------
    /** 单线程语义：所有命令方法 synchronized，模拟 Redis 的命令串行执行。 */
    static class MiniRedis {
        private final int maxKeys;
        /** LinkedHashMap accessOrder=true 实现 LRU。 */
        private final LinkedHashMap<String, Object> store;
        private final Map<String, Long> expireAt = new HashMap<>();
        final List<String> aof = new ArrayList<>();

        MiniRedis(int maxKeys) {
            this.maxKeys = maxKeys;
            this.store = new LinkedHashMap<>(16, 0.75f, true);
        }

        synchronized void set(String k, String v) { setInternal(k, v, -1); log("SET", k, v); }
        synchronized void set(String k, String v, long ttlMs) { setInternal(k, v, ttlMs); log("SET", k, v, "PX", String.valueOf(ttlMs)); }

        synchronized boolean setNx(String k, String v, long ttlMs) {
            if (lazyExpire(k) || !store.containsKey(k)) {
                setInternal(k, v, ttlMs);
                return true;
            }
            return false;
        }

        synchronized boolean delIfValueMatches(String k, String v) {
            if (v.equals(store.get(k))) {
                store.remove(k);
                expireAt.remove(k);
                return true;
            }
            return false;
        }

        synchronized String get(String k) {
            if (lazyExpire(k)) return null;
            Object v = store.get(k);
            return v == null ? null : v.toString();
        }

        synchronized long incr(String k) {
            lazyExpire(k);
            long n = Long.parseLong((String) store.getOrDefault(k, "0")) + 1;
            setInternal(k, String.valueOf(n), -1);
            log("INCR", k);
            return n;
        }

        synchronized long ttl(String k) {
            Long e = expireAt.get(k);
            return e == null ? -1 : Math.max(0, e - System.currentTimeMillis());
        }

        @SuppressWarnings("unchecked")
        synchronized void hset(String k, String f, String v) {
            ((Map<String, String>) store.computeIfAbsent(k, x -> new LinkedHashMap<String, String>())).put(f, v);
            log("HSET", k, f, v);
        }

        @SuppressWarnings("unchecked")
        synchronized Map<String, String> hgetall(String k) { return (Map<String, String>) store.getOrDefault(k, Map.of()); }

        @SuppressWarnings("unchecked")
        private Deque<String> list(String k) { return (Deque<String>) store.computeIfAbsent(k, x -> new ArrayDeque<String>()); }
        synchronized void lpush(String k, String v) { list(k).addFirst(v); log("LPUSH", k, v); }
        synchronized void rpush(String k, String v) { list(k).addLast(v); log("RPUSH", k, v); }
        synchronized String rpop(String k) { String v = list(k).pollLast(); log("RPOP", k); return v; }
        synchronized List<String> lrange(String k) { return new ArrayList<>(list(k)); }

        @SuppressWarnings("unchecked")
        private Set<String> set(String k) { return (Set<String>) store.computeIfAbsent(k, x -> new HashSet<String>()); }
        synchronized void sadd(String k, String v) { set(k).add(v); log("SADD", k, v); }
        synchronized Set<String> sinter(String a, String b) { Set<String> r = new HashSet<>(set(a)); r.retainAll(set(b)); return r; }

        /** ZSet = dict(member->score) + 有序结构(score->members)，对应 Redis 的 dict + skiplist 双结构。 */
        record ZSet(Map<String, Double> dict, NavigableMap<Double, Set<String>> sorted) {
            ZSet() { this(new HashMap<>(), new TreeMap<>()); }
        }
        synchronized void zadd(String k, double score, String member) {
            zaddRaw(k, score, member);
            log("ZADD", k, String.valueOf(score), member);
        }
        synchronized List<String> zrevrange(String k) {
            ZSet z = (ZSet) store.get(k);
            List<String> out = new ArrayList<>();
            if (z != null) {
                z.sorted.descendingMap().forEach((s, ms) -> ms.forEach(m -> out.add(m + ":" + s.intValue())));
            }
            return out;
        }

        synchronized Set<String> keys() { return new HashSet<>(store.keySet()); }

        synchronized int activeExpireCycle() {
            int removed = 0;
            for (String k : new ArrayList<>(expireAt.keySet())) {
                if (lazyExpire(k)) removed++;
            }
            return removed;
        }

        synchronized void replay(List<String> log) {
            for (String line : log) {
                String[] p = line.split(" ");
                switch (p[0]) {
                    case "SET" -> setInternal(p[1], p[2], p.length > 4 ? Long.parseLong(p[4]) : -1);
                    case "INCR" -> setInternal(p[1], String.valueOf(Long.parseLong((String) store.getOrDefault(p[1], "0")) + 1), -1);
                    case "HSET" -> hsetRaw(p[1], p[2], p[3]);
                    case "LPUSH" -> list(p[1]).addFirst(p[2]);
                    case "RPUSH" -> list(p[1]).addLast(p[2]);
                    case "RPOP" -> list(p[1]).pollLast();
                    case "SADD" -> set(p[1]).add(p[2]);
                    case "ZADD" -> zaddRaw(p[1], Double.parseDouble(p[2]), p[3]);
                    default -> { }
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void hsetRaw(String k, String f, String v) {
            ((Map<String, String>) store.computeIfAbsent(k, x -> new LinkedHashMap<String, String>())).put(f, v);
        }
        private void zaddRaw(String k, double score, String member) {
            ZSet z = (ZSet) store.computeIfAbsent(k, x -> new ZSet());
            Double old = z.dict.put(member, score);
            if (old != null) {
                z.sorted.get(old).remove(member);
            }
            z.sorted.computeIfAbsent(score, s -> new HashSet<>()).add(member);
        }

        private void setInternal(String k, Object v, long ttlMs) {
            if (!store.containsKey(k) && store.size() >= maxKeys) {
                String eldest = store.keySet().iterator().next(); // accessOrder 下第一个即最久未访问
                store.remove(eldest);
                expireAt.remove(eldest);
            }
            store.put(k, v);
            if (ttlMs > 0) expireAt.put(k, System.currentTimeMillis() + ttlMs); else expireAt.remove(k);
        }

        private boolean lazyExpire(String k) {
            Long e = expireAt.get(k);
            if (e != null && System.currentTimeMillis() > e) {
                store.remove(k);
                expireAt.remove(k);
                return true;
            }
            return false;
        }

        private void log(String... cmd) { aof.add(String.join(" ", cmd)); }
    }
}
