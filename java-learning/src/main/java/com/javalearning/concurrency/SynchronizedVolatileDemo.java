package com.javalearning.concurrency;

import java.util.concurrent.CountDownLatch;

/**
 * 知识点 3-3：synchronized 与 volatile。
 *
 * <p>重点：synchronized 的三种用法与对应锁对象、锁升级（偏向锁 → 轻量级锁 → 重量级锁）、
 * 可重入性、wait/notify 必须在同步块内、volatile 不能替代锁（i++ 不是原子的）、DCL 单例。
 */
public class SynchronizedVolatileDemo {

    static int unsafeCounter = 0;
    static volatile int volatileCounter = 0;
    static int syncCounter = 0;
    static final Object LOCK = new Object();

    static class Singleton {
        private static volatile Singleton instance;

        private Singleton() { }

        static Singleton getInstance() {
            if (instance == null) {                      // 第一次检查：避免每次都加锁
                synchronized (Singleton.class) {
                    if (instance == null) {              // 第二次检查：避免重复创建
                        instance = new Singleton();      // volatile 禁止 "赋值引用" 与 "初始化" 重排
                    }
                }
            }
            return instance;
        }
    }

    /** 可重入：同一线程可以重复获得同一把锁，否则 inner() 会死锁。 */
    static synchronized void outer() {
        inner();
    }

    static synchronized void inner() {
        System.out.println("  可重入：outer() 持有 Class 锁时再次进入 inner() 成功");
    }

    public static void main(String[] args) throws Exception {
        counterRace();
        outer();
        waitNotify();
        System.out.println("DCL 单例: " + (Singleton.getInstance() == Singleton.getInstance()));
        System.out.println("""

                synchronized 三种形式：
                  修饰实例方法 -> 锁 this；修饰静态方法 -> 锁 Class 对象；同步块 -> 锁括号内对象
                锁升级（JDK 15 起默认禁用偏向锁）：
                  无锁 -> 偏向锁(只有一个线程) -> 轻量级锁(CAS 自旋, 交替执行) -> 重量级锁(OS mutex, 真正竞争)
                  锁状态记录在对象头 Mark Word 中，升级不可逆
                volatile vs synchronized：
                  volatile 只保证可见性与有序性，适合 "一写多读" 的标志位；
                  复合操作（读-改-写）需要 synchronized / Lock / Atomic 类
                """);
    }

    static void counterRace() throws InterruptedException {
        System.out.println("== 三种计数器 ==");
        int threads = 8, loops = 10_000;
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                for (int j = 0; j < loops; j++) {
                    unsafeCounter++;
                    volatileCounter++;              // 读-改-写三步，不原子
                    synchronized (LOCK) {
                        syncCounter++;
                    }
                }
                done.countDown();
            }).start();
        }
        done.await();
        int expected = threads * loops;
        System.out.printf("  期望 %d：普通 int = %d，volatile int = %d，synchronized = %d%n",
                expected, unsafeCounter, volatileCounter, syncCounter);
        System.out.println("  volatile 计数同样丢失更新，证明 volatile 不保证原子性");
    }

    /** 生产者-消费者：wait/notify 必须持有对象监视器，且用 while 而非 if 防止虚假唤醒。 */
    static void waitNotify() throws InterruptedException {
        System.out.println("\n== wait / notify ==");
        final int[] buffer = new int[1];
        final boolean[] hasValue = {false};
        Thread consumer = new Thread(() -> {
            synchronized (buffer) {
                while (!hasValue[0]) {
                    try {
                        buffer.wait(); // 释放锁并等待
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                System.out.println("  消费者取到 " + buffer[0]);
            }
        });
        consumer.start();
        Thread.sleep(100);
        synchronized (buffer) {
            buffer[0] = 42;
            hasValue[0] = true;
            buffer.notifyAll(); // 唤醒等待者（优先 notifyAll 避免信号丢失）
        }
        consumer.join();
        try {
            buffer.wait();
        } catch (IllegalMonitorStateException e) {
            System.out.println("  不持有监视器调用 wait -> IllegalMonitorStateException");
        }
    }
}
