package com.javalearning.jvm;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识点 4-8：OOM 与内存泄漏排查。
 *
 * <p>重点：OutOfMemoryError 的几种类型及典型原因（Java heap space / Metaspace / unable to create native thread / Direct buffer memory / GC overhead limit）、
 * Java 中的"内存泄漏"是对象仍被引用却不再使用、三种经典泄漏（静态集合、未 remove 的 ThreadLocal、未注销的监听器 / 可变 key），
 * 以及通过 jmap + MAT 定位泄漏链的方法。示例以受控方式演示堆 OOM。
 */
public class OomDemo {

    /** 典型泄漏 1：静态集合只增不减（缓存没有淘汰策略）。 */
    static final List<byte[]> STATIC_CACHE = new ArrayList<>();

    /** 典型泄漏 2：可变对象作为 HashMap key，修改后 hashCode 改变，再也无法 remove。 */
    static class MutableKey {
        int id;

        MutableKey(int id) { this.id = id; }

        @Override
        public int hashCode() { return id; }

        @Override
        public boolean equals(Object o) { return o instanceof MutableKey k && k.id == id; }
    }

    public static void main(String[] args) {
        System.out.println("== 当前最大堆: " + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / 1024 / 1024 + " MB ==");
        System.out.println("建议以 -Xmx64m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp 运行本示例，再用 MAT 打开 hprof");

        mutableKeyLeak();
        heapOom();

        System.out.println("""

                OOM 类型速查：
                  java.lang.OutOfMemoryError: Java heap space          对象太多/太大，或泄漏；看 jmap -histo 与堆转储支配树
                  java.lang.OutOfMemoryError: GC overhead limit exceeded  98% 时间在 GC 却只回收 2%，是堆 OOM 的前奏
                  java.lang.OutOfMemoryError: Metaspace                 类加载过多（动态代理、Groovy 脚本、热部署），调 MaxMetaspaceSize 或修泄漏
                  java.lang.OutOfMemoryError: unable to create native thread  线程数超限，检查线程池是否无界、ulimit -u
                  java.lang.OutOfMemoryError: Direct buffer memory      NIO 堆外内存，Netty 需调 -XX:MaxDirectMemorySize 并检查 ByteBuf 释放
                  java.lang.StackOverflowError                          递归太深或 -Xss 太小（不是 OOM，但常一起考）
                排查流程：
                  1. 开启 -XX:+HeapDumpOnOutOfMemoryError 拿到 hprof
                  2. MAT -> Leak Suspects / Dominator Tree 看谁占了内存
                  3. 顺着 Path to GC Roots（排除弱/软引用）找到持有者，定位代码
                  4. 线上无 dump 时：jmap -histo:live 多次对比，持续增长的类就是嫌疑人
                """);
    }

    static void mutableKeyLeak() {
        System.out.println("\n== 可变 key 导致的泄漏 ==");
        Map<MutableKey, String> map = new HashMap<>();
        MutableKey key = new MutableKey(1);
        map.put(key, "value");
        key.id = 2;                  // hash 变了，对象仍停留在 hash(1) 的桶里
        map.remove(key);             // 去 hash(2) 的桶找，找不到
        System.out.println("  remove 后 size = " + map.size() + "，这个 Entry 永远无法被访问也无法被移除 -> 泄漏");
    }

    static void heapOom() {
        System.out.println("\n== 受控地触发堆 OOM ==");
        long maxHeap = Runtime.getRuntime().maxMemory();
        int chunk = 10 * 1024 * 1024;
        int allocated = 0;
        try {
            while (true) {
                STATIC_CACHE.add(new byte[chunk]);
                allocated++;
                if ((long) allocated * chunk > maxHeap + 512L * 1024 * 1024) {
                    System.out.println("  堆似乎没有上限（可能是极大 -Xmx），停止演示以免影响机器");
                    break;
                }
            }
        } catch (OutOfMemoryError e) {
            STATIC_CACHE.clear(); // 先释放再打印，否则连 println 都可能失败
            System.out.println("  分配约 " + allocated * 10 + " MB 后抛出 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("  这些 byte[] 被静态集合强引用，GC 无法回收——这就是\"泄漏\"与\"真的需要这么多内存\"的区别");
        }
    }
}
