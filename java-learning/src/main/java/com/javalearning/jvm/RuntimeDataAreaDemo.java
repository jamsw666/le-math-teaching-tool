package com.javalearning.jvm;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

/**
 * 知识点 4-2：运行时数据区（堆 / 栈 / 方法区）。
 *
 * <p>重点：线程私有区（程序计数器、虚拟机栈、本地方法栈）与线程共享区（堆、方法区/元空间）、
 * 栈帧结构（局部变量表、操作数栈、动态链接、返回地址）、StackOverflowError 演示、
 * 通过 MXBean 观察堆各代与元空间的实际大小。
 */
public class RuntimeDataAreaDemo {

    static int depth = 0;

    static void recurse() {
        depth++;
        recurse(); // 每次调用压入一个栈帧，直到栈空间耗尽
    }

    public static void main(String[] args) {
        stackOverflow();
        memoryPools();
        System.out.println("""

                运行时数据区划分：
                  线程私有：程序计数器（唯一不会 OOM 的区域）、虚拟机栈（栈帧：局部变量表/操作数栈/动态链接/返回地址）、本地方法栈
                  线程共享：堆（新生代 Eden + 2 个 Survivor，老年代；对象实例与数组）、方法区（JDK 8 起由元空间实现，使用本地内存：类元信息、运行时常量池、JIT 代码缓存）
                  直接内存：NIO 的 DirectByteBuffer，受 -XX:MaxDirectMemorySize 限制，不算在堆内
                常用参数：-Xms/-Xmx 堆初始/最大，-Xss 线程栈，-Xmn 新生代，-XX:MetaspaceSize/MaxMetaspaceSize，-XX:+HeapDumpOnOutOfMemoryError
                """);
    }

    static void stackOverflow() {
        System.out.println("== 虚拟机栈深度 ==");
        try {
            recurse();
        } catch (StackOverflowError e) {
            System.out.println("  递归 " + depth + " 层后 StackOverflowError（默认 -Xss 通常为 512KB~1MB，局部变量越多可递归层数越少）");
        }
    }

    static void memoryPools() {
        System.out.println("\n== 内存池（MXBean） ==");
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        System.out.printf("  堆: 已用 %d MB / 已提交 %d MB / 最大 %d MB%n", mb(heap.getUsed()), mb(heap.getCommitted()), mb(heap.getMax()));
        System.out.printf("  非堆（元空间 + 代码缓存等）: 已用 %d MB%n", mb(nonHeap.getUsed()));
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage u = pool.getUsage();
            System.out.printf("  %-28s %-8s 已用 %6d KB%n", pool.getName(), pool.getType(), u.getUsed() / 1024);
        }
        System.out.println("  G1 的池名为 G1 Eden Space / G1 Survivor Space / G1 Old Gen；Metaspace 与 Compressed Class Space 属于非堆");
        byte[] big = new byte[8 * 1024 * 1024];
        System.out.println("  分配 8MB 数组后堆已用: " + mb(memory.getHeapMemoryUsage().getUsed()) + " MB（大对象在 G1 下进入 Humongous 区域，长度=" + big.length + "）");
    }

    static long mb(long bytes) {
        return bytes < 0 ? -1 : bytes / 1024 / 1024;
    }
}
