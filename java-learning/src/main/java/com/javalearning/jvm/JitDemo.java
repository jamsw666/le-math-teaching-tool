package com.javalearning.jvm;

import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;

/**
 * 知识点 4-5：JIT 即时编译与逃逸分析。
 *
 * <p>重点：解释执行 → C1（客户端，快速编译）→ C2（服务端，深度优化）的分层编译、热点探测（方法调用计数 + 回边计数）、
 * OSR 栈上替换、常见优化（内联、逃逸分析、锁消除、循环展开、分支预测）、预热对基准测试的意义、JMH。
 */
public class JitDemo {

    static int add(int a, int b) {
        return a + b; // 小方法会被内联，调用开销消失
    }

    /** 锁对象不逃逸 -> JIT 锁消除，synchronized 几乎零成本。 */
    static int lockElimination(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            Object local = new Object();
            synchronized (local) {
                sum += i;
            }
        }
        return sum;
    }

    static long hotLoop(int n) {
        long acc = 0;
        for (int i = 0; i < n; i++) {
            acc += add(i, 1);
        }
        return acc;
    }

    public static void main(String[] args) {
        CompilationMXBean compiler = ManagementFactory.getCompilationMXBean();
        System.out.println("== JIT 编译器 ==");
        System.out.println("  名称: " + compiler.getName() + "，是否支持统计编译时间: " + compiler.isCompilationTimeMonitoringSupported());

        System.out.println("\n== 预热前后性能变化 ==");
        for (int round = 1; round <= 5; round++) {
            long t = System.nanoTime();
            long r = hotLoop(20_000_000);
            long elapsed = System.nanoTime() - t;
            System.out.printf("  第 %d 轮: %.1f ms（累计编译时间 %d ms）(r=%d)%n", round, elapsed / 1e6, compiler.getTotalCompilationTime(), r);
        }
        System.out.println("  前几轮由解释器 + C1 执行，之后 C2 编译完成速度大幅提升——这就是基准测试必须预热的原因");

        System.out.println("\n== 锁消除 ==");
        long t = System.nanoTime();
        int s = lockElimination(50_000_000);
        System.out.printf("  5000 万次对不逃逸对象加锁耗时 %.1f ms（锁被 JIT 消除）(s=%d)%n", (System.nanoTime() - t) / 1e6, s);

        System.out.println("""

                分层编译（-XX:+TieredCompilation 默认开启）：
                  Level 0 解释执行 -> Level 1-3 C1 编译（带不同程度的 profiling）-> Level 4 C2 编译（基于 profile 的激进优化）
                  热点阈值：-XX:Tier4InvocationThreshold=15000 / Tier4BackEdgeThreshold=40000（回边计数支持循环内 OSR）
                常见优化：
                  方法内联（最重要，-XX:MaxInlineSize=35 字节，热点方法 325 字节）、逃逸分析（标量替换 / 栈上分配 / 锁消除）
                  循环展开与向量化、分支预测与去虚拟化（单态/双态内联缓存）、空检查消除、范围检查消除
                去优化（Deoptimization）：当假设被打破（如出现新子类）时退回解释执行再重新编译
                观察手段：-XX:+PrintCompilation；-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining；JITWatch 可视化
                正确做基准测试请用 OpenJDK JMH（@Warmup / @Measurement / Blackhole 防止死代码消除）
                GraalVM Native Image 走 AOT 路线：启动极快、内存小，但失去运行时 profile 优化与部分反射动态性
                """);
    }
}
