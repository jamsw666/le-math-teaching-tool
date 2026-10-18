package com.javalearning.jvm;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识点 4-4：垃圾回收算法与收集器（CMS / G1 / ZGC）。
 *
 * <p>重点：可达性分析与 GC Roots、四种引用强度、分代假说与对象晋升、
 * 标记-清除 / 标记-复制 / 标记-整理、Minor GC 与 Full GC 的触发、
 * 常见收集器组合及其停顿特征，通过 MXBean 观察本进程使用的收集器与 GC 次数。
 */
public class GcDemo {

    static class Big {
        final byte[] payload = new byte[1024 * 1024]; // 1MB
    }

    public static void main(String[] args) throws Exception {
        collectors();
        referenceTypes();
        generational();
        System.out.println("""

                算法：
                  标记-清除：简单，但产生碎片（CMS 老年代）
                  标记-复制：无碎片，需要额外空间，存活对象少时效率高（新生代 Eden:S0:S1 = 8:1:1）
                  标记-整理：无碎片、无额外空间，但移动对象需要 STW（Serial Old / Parallel Old / G1 混合回收）
                收集器演进：
                  Serial / Parallel（吞吐优先，Parallel 是 JDK 8 默认）
                  CMS（并发标记清除，低停顿，JDK 14 移除）
                  G1（JDK 9+ 默认，Region 化堆，可预测停顿 -XX:MaxGCPauseMillis，Mixed GC）
                  ZGC / Shenandoah（JDK 15/12 生产可用，着色指针 + 读屏障，停顿 < 1ms 且与堆大小无关；JDK 21 ZGC 分代）
                GC 调优方向：先看 GC 日志（-Xlog:gc*），确认是分配过快、晋升过早还是内存泄漏，再调堆大小/新生代比例/选择收集器
                """);
    }

    static void collectors() {
        System.out.println("== 当前 JVM 使用的收集器 ==");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.printf("  %-24s 回收次数 %d 累计耗时 %d ms 管理的内存池 %s%n",
                    gc.getName(), gc.getCollectionCount(), gc.getCollectionTime(), String.join(",", gc.getMemoryPoolNames()));
        }
        System.out.println("  G1 Young Generation / G1 Old Generation 说明是 G1；切换: -XX:+UseZGC / -XX:+UseParallelGC / -XX:+UseSerialGC");
    }

    static void referenceTypes() throws InterruptedException {
        System.out.println("\n== 四种引用 ==");
        Object strong = new Object();
        SoftReference<byte[]> soft = new SoftReference<>(new byte[1024]);
        WeakReference<Object> weak = new WeakReference<>(new Object());
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        PhantomReference<Object> phantom = new PhantomReference<>(new Object(), queue);

        System.gc();
        Thread.sleep(100);
        System.out.println("  强引用存活: " + (strong != null));
        System.out.println("  软引用 GC 后仍在（内存不足才回收，适合缓存）: " + (soft.get() != null));
        System.out.println("  弱引用 GC 后被回收（WeakHashMap / ThreadLocal key）: " + (weak.get() == null));
        System.out.println("  虚引用 get() 永远为 null，仅用于回收通知（DirectByteBuffer 的 Cleaner）: " + (phantom.get() == null)
                + "，已入队: " + (queue.poll() != null));
    }

    static void generational() {
        System.out.println("\n== 分代回收观察 ==");
        long youngBefore = gcCount("Young");
        List<Big> survivors = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Big b = new Big();         // 大量短命对象在 Eden 分配并很快死亡
            if (i % 50 == 0) survivors.add(b); // 少数对象长期存活，多次 Minor GC 后晋升老年代（-XX:MaxTenuringThreshold 默认 15）
        }
        long youngAfter = gcCount("Young");
        System.out.println("  分配 200MB 临时对象期间发生 Minor GC 约 " + (youngAfter - youngBefore) + " 次，长期存活 " + survivors.size() + " 个");
        System.out.println("  GC Roots：虚拟机栈局部变量、静态字段、常量、JNI 引用、活跃线程、同步锁对象、JMXBean 等");
        System.out.println("  Full GC 触发：老年代空间不足、元空间不足、System.gc()（可用 -XX:+DisableExplicitGC 禁用）、CMS 并发失败、空间分配担保失败");
    }

    static long gcCount(String keyword) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .filter(gc -> gc.getName().contains(keyword))
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .sum();
    }
}
