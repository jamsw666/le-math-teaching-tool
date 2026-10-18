package com.javalearning.jvm;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识点 4-3：对象创建与内存布局。
 *
 * <p>重点：new 的五步（检查类加载 → 分配内存 TLAB/指针碰撞/空闲列表 → 零值初始化 → 设置对象头 → 执行 &lt;init&gt;）、
 * 对象头（Mark Word + Klass Pointer）、实例数据与对齐填充、压缩指针、逃逸分析与栈上分配/标量替换、对象引用的访问方式。
 */
public class ObjectLayoutDemo {

    static class Empty { }

    static class WithFields {
        byte b;      // 1
        int i;       // 4
        long l;      // 8
        Object ref;  // 4（压缩指针）或 8
    }

    /** 不会逃逸出方法的对象，JIT 可能做标量替换直接放寄存器/栈上，不在堆分配。 */
    static int noEscape(int x) {
        WithFields tmp = new WithFields();
        tmp.i = x;
        return tmp.i * 2;
    }

    public static void main(String[] args) {
        layoutEstimate();
        escapeAnalysis();
        System.out.println("""

                对象创建步骤：
                  1. 检查常量池中的符号引用对应的类是否已加载、解析、初始化
                  2. 分配内存：堆规整用"指针碰撞"（Serial/ParNew/G1），不规整用"空闲列表"（CMS）；并发安全靠 TLAB（线程本地分配缓冲）或 CAS
                  3. 内存零值初始化（所以字段不赋值也有默认值）
                  4. 设置对象头：哈希码、GC 分代年龄、锁状态（Mark Word）+ 类型指针（Klass Pointer）+ 数组长度
                  5. 执行 <init>（构造器）——这一步之前对象已存在，这就是 DCL 需要 volatile 的原因
                对象布局 = 对象头(12 字节, 开启压缩指针) + 实例数据(按 long/double > int > short/char > byte/boolean > 引用 重排) + 对齐填充(8 字节倍数)
                逃逸分析（-XX:+DoEscapeAnalysis 默认开）：对象不逃逸则可 栈上分配 / 标量替换 / 锁消除
                精确测量请用 OpenJDK JOL：ClassLayout.parseInstance(obj).toPrintable()
                """);
    }

    static void layoutEstimate() {
        System.out.println("== 对象大小估算 ==");
        boolean compressedOops = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .noneMatch(a -> a.contains("-UseCompressedOops"));
        int header = compressedOops ? 12 : 16;
        int refSize = compressedOops ? 4 : 8;
        System.out.println("  压缩指针(默认堆 < 32GB 开启): " + compressedOops + "，对象头 = " + header + " 字节，引用 = " + refSize + " 字节");
        System.out.println("  new Empty()      ≈ " + align(header) + " 字节（对象头 + 填充到 8 的倍数）");
        int fields = 1 + 4 + 8 + refSize;
        System.out.println("  new WithFields() ≈ " + align(header + fields) + " 字节（头 " + header + " + 字段 " + fields + " + 填充）");
        System.out.println("  new int[10]      ≈ " + align(header + 4 + 10 * 4) + " 字节（头 + 4 字节长度 + 元素）");
        System.out.println("  Integer 对象 16 字节，而 int 只要 4 字节 -> 大数组优先用基本类型 / 原生集合库（fastutil、Eclipse Collections）");
    }

    static void escapeAnalysis() {
        System.out.println("\n== 逃逸分析效果 ==");
        long before = usedMemory();
        long t = System.nanoTime();
        long acc = 0;
        for (int i = 0; i < 50_000_000; i++) {
            acc += noEscape(i);
        }
        long elapsed = System.nanoTime() - t;
        long after = usedMemory();
        System.out.printf("  5000 万次创建不逃逸对象耗时 %.0f ms，堆增长约 %d MB（接近 0 说明被标量替换，未在堆上分配）(acc=%d)%n",
                elapsed / 1e6, Math.max(0, (after - before) / 1024 / 1024), acc);
        System.out.println("  对比运行 -XX:-DoEscapeAnalysis 可看到堆明显增长、GC 次数上升");
        List<Object> keep = new ArrayList<>();
        keep.add(new WithFields());
        System.out.println("  加入集合的对象会逃逸（被外部引用），必须在堆上分配: " + keep.size() + " 个");
    }

    static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    static int align(int size) {
        return (size + 7) / 8 * 8;
    }
}
