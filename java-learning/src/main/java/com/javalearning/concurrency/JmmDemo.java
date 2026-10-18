package com.javalearning.concurrency;

import java.util.concurrent.CountDownLatch;

/**
 * 知识点 3-2：Java 内存模型（JMM）。
 *
 * <p>重点：主内存与工作内存、三大特性（原子性、可见性、有序性）、happens-before 规则、
 * 指令重排与 volatile 的内存屏障、DCL 单例为什么需要 volatile。
 */
public class JmmDemo {

    static boolean plainFlag = false;
    static volatile boolean volatileFlag = false;

    static int x = 0, y = 0;
    static int a = 0, b = 0;

    public static void main(String[] args) throws Exception {
        visibility();
        reordering();
        System.out.println("""

                happens-before 八条核心规则（前者的写对后者可见）：
                  1. 程序顺序规则：同一线程内按代码顺序
                  2. 监视器锁规则：unlock 先于后续对同一锁的 lock
                  3. volatile 规则：volatile 写先于后续对它的读
                  4. 线程启动规则：Thread.start() 先于线程内任何操作
                  5. 线程终止规则：线程内所有操作先于 join() 返回
                  6. 中断规则：interrupt() 先于被中断线程检测到中断
                  7. 终结器规则：构造函数结束先于 finalize()
                  8. 传递性：A hb B, B hb C => A hb C
                volatile 保证可见性 + 禁止重排，但不保证复合操作（i++）的原子性
                """);
    }

    /** 可见性：普通变量的修改可能长期停留在某个线程的工作内存/寄存器中。 */
    static void visibility() throws InterruptedException {
        System.out.println("== 可见性 ==");
        Thread plainReader = new Thread(() -> {
            long spins = 0;
            while (!plainFlag) {
                spins++; // 空循环，JIT 可能把 plainFlag 提升到寄存器，导致永远看不到修改
            }
            System.out.println("  plainReader 退出，自旋 " + spins + " 次");
        });
        plainReader.setDaemon(true);
        plainReader.start();
        Thread.sleep(100);
        plainFlag = true;
        plainReader.join(1000);
        System.out.println("  普通 boolean 标志位：读线程 " + (plainReader.isAlive() ? "仍在死循环（修改不可见）" : "正常退出（本次恰好可见，不代表总是如此）"));

        Thread volatileReader = new Thread(() -> {
            while (!volatileFlag) { }
            System.out.println("  volatileReader 退出");
        });
        volatileReader.start();
        Thread.sleep(100);
        volatileFlag = true;
        volatileReader.join(1000);
        System.out.println("  volatile 标志位：读线程 " + (volatileReader.isAlive() ? "仍在运行（不应发生）" : "正常退出（写后立即刷新到主内存并使其他缓存失效）"));
    }

    /** 有序性：两个线程各自先写后读，如果没有重排，(x, y) 不可能同时为 (0, 0)。 */
    static void reordering() throws InterruptedException {
        System.out.println("\n== 指令重排 ==");
        int bothZero = 0;
        int iterations = 20_000;
        for (int i = 0; i < iterations; i++) {
            x = 0; y = 0; a = 0; b = 0;
            CountDownLatch start = new CountDownLatch(1);
            Thread t1 = new Thread(() -> {
                await(start);
                a = 1;
                x = b;
            });
            Thread t2 = new Thread(() -> {
                await(start);
                b = 1;
                y = a;
            });
            t1.start();
            t2.start();
            start.countDown();
            t1.join();
            t2.join();
            if (x == 0 && y == 0) bothZero++;
        }
        System.out.println("  " + iterations + " 轮中 (x, y) == (0, 0) 出现 " + bothZero + " 次"
                + (bothZero > 0 ? "（出现即证明发生了 store-load 重排）" : "（本机未观察到，不代表不会发生；把 a/b 声明为 volatile 可彻底消除）"));
        System.out.println("  DCL 单例必须 volatile：instance = new Singleton() 分为 分配内存 -> 初始化 -> 赋值引用 三步，2、3 可能重排，导致其他线程拿到未初始化对象");
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
