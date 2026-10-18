package com.javalearning.collections;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识点 2-3：Map —— HashMap / TreeMap / ConcurrentHashMap。
 *
 * <p>重点：HashMap 的 hash 扰动与寻址、链表转红黑树阈值、负载因子与扩容、
 * LinkedHashMap 实现 LRU、TreeMap 的范围查询、ConcurrentHashMap 的 CAS + synchronized 分桶锁以及 computeIfAbsent 原子性。
 */
public class MapDemo {

    /** 用 LinkedHashMap 的访问顺序模式 + removeEldestEntry 实现 LRU 缓存。 */
    static class LruCache<K, V> extends LinkedHashMap<K, V> {
        private final int capacity;

        LruCache(int capacity) {
            super(16, 0.75f, true); // accessOrder = true
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > capacity;
        }
    }

    public static void main(String[] args) {
        hashMapInternals();
        lruCache();
        treeMapRange();
        concurrentHashMap();
    }

    static void hashMapInternals() {
        System.out.println("== HashMap 原理 ==");
        int h = "hello".hashCode();
        int spread = h ^ (h >>> 16);
        System.out.printf("hashCode = %08x，扰动后 hash = %08x（高 16 位异或到低 16 位，减少碰撞）%n", h, spread);
        System.out.println("桶下标 = (table.length - 1) & hash = " + ((16 - 1) & spread) + "（容量 16）");
        System.out.println("默认容量 16，负载因子 0.75，size > 12 时扩容为 2 倍；扩容时元素要么留在原桶 i，要么去 i + oldCap");
        System.out.println("单桶链表长度 >= 8 且 table >= 64 时树化为红黑树，<= 6 时退化为链表");
        System.out.println("HashMap 允许一个 null key；线程不安全，并发 put 可能丢数据（JDK 7 还会成环死循环）");

        Map<String, Integer> wordCount = new HashMap<>();
        for (String w : "a b a c b a".split(" ")) {
            wordCount.merge(w, 1, Integer::sum);
        }
        System.out.println("merge 统计词频: " + wordCount + "，getOrDefault(\"z\", 0) = " + wordCount.getOrDefault("z", 0));
    }

    static void lruCache() {
        System.out.println("\n== LinkedHashMap 实现 LRU ==");
        LruCache<String, Integer> cache = new LruCache<>(3);
        cache.put("a", 1); cache.put("b", 2); cache.put("c", 3);
        cache.get("a");          // 访问 a，使其变为最近使用
        cache.put("d", 4);       // 容量超限，淘汰最久未使用的 b
        System.out.println("put a,b,c -> get a -> put d 后: " + cache.keySet() + "（b 被淘汰）");
    }

    static void treeMapRange() {
        System.out.println("\n== TreeMap 范围查询 ==");
        TreeMap<Integer, String> scores = new TreeMap<>(Map.of(60, "及格", 80, "良好", 90, "优秀"));
        int score = 85;
        System.out.println("85 分等级 = " + scores.floorEntry(score).getValue() + "（floorEntry 找 <= key 的最大项）");
        System.out.println("[60, 90) 区间: " + scores.subMap(60, 90) + "，firstKey=" + scores.firstKey() + " lastKey=" + scores.lastKey());
        System.out.println("TreeMap 基于红黑树，增删查 O(log n)，key 必须可比较且不允许 null");
    }

    static void concurrentHashMap() throws RuntimeException {
        System.out.println("\n== ConcurrentHashMap ==");
        ConcurrentHashMap<String, Integer> chm = new ConcurrentHashMap<>();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) chm.merge("counter", 1, Integer::sum);
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        System.out.println("8 线程各 merge 1000 次 = " + chm.get("counter") + "（merge/compute 是原子的）");
        System.out.println("JDK 8+：Node 数组 + CAS 插入空桶 + synchronized 锁单个桶头节点，读操作无锁（volatile）");
        System.out.println("不允许 null key/value；size() 用 CounterCell 分段计数；computeIfAbsent 内部不要再修改同一 map（可能死循环）");
        System.out.println("Hashtable / Collections.synchronizedMap 是全表一把锁，已不推荐");
    }
}
