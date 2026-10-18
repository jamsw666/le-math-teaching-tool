package com.javalearning.modern;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * 知识点 6-8：虚拟线程 Virtual Thread（Java 21，JEP 444）。
 *
 * <p>重点：虚拟线程是 JVM 调度的用户态线程，阻塞时自动让出载体线程（ForkJoinPool）；
 * 创建成本极低可开百万级；适合 IO 密集 / 每请求一线程模型，不适合 CPU 密集；
 * pinning 问题（synchronized 内阻塞会钉住载体线程，Java 21 中应改用 ReentrantLock）；
 * 不要池化虚拟线程、慎用 ThreadLocal；结构化并发（预览）。
 */
public class VirtualThreadDemo {

    static void blockingIo(int millis) {
        try {
            Thread.sleep(millis); // 模拟阻塞 IO：虚拟线程在此处会卸载（unmount），不占用载体线程
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        creation();
        massiveConcurrency();
        pinning();
        System.out.println("""

                原理：
                  虚拟线程的栈保存在堆上（可增长的 chunk），由 JVM 在少量载体线程（默认 = CPU 核数的 ForkJoinPool）上调度
                  调用阻塞 API（sleep、Socket read、Lock、BlockingQueue）时 JVM 把虚拟线程 unmount，载体线程去跑别的虚拟线程
                  因此"一请求一线程"的同步写法重新变得可伸缩，不必为吞吐量改写成响应式/回调风格
                使用准则：
                  - 用 Executors.newVirtualThreadPerTaskExecutor()，每个任务一个新虚拟线程，不要池化（池化毫无意义）
                  - 适合高并发 IO（HTTP 服务、RPC 客户端、数据库访问）；CPU 密集任务不会变快
                  - Java 21 中 synchronized 块内阻塞 / native 调用会 pinning 载体线程 -> 改用 ReentrantLock；Java 24 (JEP 491) 已修复
                  - ThreadLocal 仍可用但百万线程下内存可观，考虑 ScopedValue (JEP 446)
                  - 排查 pinning：-Djdk.tracePinnedThreads=full；观测：jcmd <pid> Thread.dump_to_file -format=json
                生态：Spring Boot 3.2 一行配置 spring.threads.virtual.enabled=true；Tomcat/Jetty/Helidon/Quarkus 均已支持
                """);
    }

    static void creation() throws InterruptedException {
        System.out.println("== 创建方式 ==");
        Thread vt = Thread.ofVirtual().name("vt-demo").start(() ->
                System.out.println("  " + Thread.currentThread() + " isVirtual=" + Thread.currentThread().isVirtual()
                        + " isDaemon=" + Thread.currentThread().isDaemon()));
        vt.join();
        Thread pt = Thread.ofPlatform().name("pt-demo").start(() ->
                System.out.println("  " + Thread.currentThread() + " isVirtual=" + Thread.currentThread().isVirtual()));
        pt.join();
        System.out.println("  还可以 Thread.startVirtualThread(r) / Executors.newVirtualThreadPerTaskExecutor()；虚拟线程总是守护线程，优先级固定");
    }

    static void massiveConcurrency() throws Exception {
        System.out.println("\n== 10 万个阻塞任务：平台线程池 vs 虚拟线程 ==");
        int tasks = 100_000;
        AtomicInteger done = new AtomicInteger();

        Instant t0 = Instant.now();
        try (ExecutorService pool = Executors.newFixedThreadPool(200)) {
            IntStream.range(0, tasks).forEach(i -> pool.submit(() -> {
                blockingIo(10);
                done.incrementAndGet();
            }));
        } // ExecutorService 实现了 AutoCloseable（Java 19+），close 会等待任务完成
        Duration platform = Duration.between(t0, Instant.now());

        done.set(0);
        t0 = Instant.now();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, tasks).forEach(i -> executor.submit(() -> {
                blockingIo(10);
                done.incrementAndGet();
            }));
        }
        Duration virtual = Duration.between(t0, Instant.now());
        System.out.printf("  200 个平台线程池: %d ms（理论下限 100000*10/200 = 5000 ms）%n", platform.toMillis());
        System.out.printf("  虚拟线程:         %d ms（完成 %d 个，几乎只受 sleep 10ms 本身限制）%n", virtual.toMillis(), done.get());
        System.out.println("  载体线程数 = " + Runtime.getRuntime().availableProcessors() + "（jdk.virtualThreadScheduler.parallelism），却跑了 10 万个并发任务");
    }

    static void pinning() throws Exception {
        System.out.println("\n== pinning：synchronized vs ReentrantLock ==");
        int locks = 100, n = 2000;              // 100 把互不相关的锁，理论上允许 100 路并行
        Object[] monitors = new Object[locks];
        ReentrantLock[] reentrantLocks = new ReentrantLock[locks];
        for (int i = 0; i < locks; i++) {
            monitors[i] = new Object();
            reentrantLocks[i] = new ReentrantLock();
        }

        long t = System.nanoTime();
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                Object monitor = monitors[i % locks];
                ex.submit(() -> {
                    synchronized (monitor) {
                        blockingIo(5); // Java 21：在 synchronized 内阻塞会把载体线程钉住，并发度退化为载体线程数
                    }
                });
            }
        }
        long syncMs = (System.nanoTime() - t) / 1_000_000;

        t = System.nanoTime();
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                ReentrantLock lock = reentrantLocks[i % locks];
                ex.submit(() -> {
                    lock.lock();
                    try {
                        blockingIo(5); // ReentrantLock 阻塞时虚拟线程可以正常卸载
                    } finally {
                        lock.unlock();
                    }
                });
            }
        }
        long lockMs = (System.nanoTime() - t) / 1_000_000;
        int carriers = Runtime.getRuntime().availableProcessors();
        System.out.printf("  %d 个任务分布在 %d 把锁上、每个持锁 5ms：synchronized %d ms，ReentrantLock %d ms%n", n, locks, syncMs, lockMs);
        System.out.printf("  理论值：ReentrantLock ≈ %d/%d*5 = %d ms；synchronized 被 pinning 后并发度只有载体线程数 %d，≈ %d/%d*5 = %d ms%n",
                n, locks, n / locks * 5, carriers, n, carriers, n / carriers * 5);
        System.out.println("  （随机数 " + ThreadLocalRandom.current().nextInt(10) + " 仅为避免 JIT 优化）");
    }
}
