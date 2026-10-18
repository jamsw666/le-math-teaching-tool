package com.javalearning.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 知识点 3-1：线程生命周期与线程池（ThreadPoolExecutor）。
 *
 * <p>重点：线程 6 种状态、ThreadPoolExecutor 7 个参数与任务提交流程（核心线程 → 队列 → 非核心线程 → 拒绝策略）、
 * 为什么不推荐 Executors 工厂方法、优雅关闭、Future 获取结果。
 */
public class ThreadPoolDemo {

    public static void main(String[] args) throws Exception {
        threadStates();
        submissionFlow();
        futureAndShutdown();
    }

    static void threadStates() throws InterruptedException {
        System.out.println("== 线程状态 ==");
        Object lock = new Object();
        Thread t = new Thread(() -> {
            synchronized (lock) {
                try {
                    lock.wait(); // WAITING
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "state-demo");
        System.out.println("new 之后: " + t.getState());
        t.start();
        Thread.sleep(50);
        System.out.println("wait() 中: " + t.getState());
        synchronized (lock) {
            lock.notify();
        }
        t.join();
        System.out.println("结束后: " + t.getState());
        System.out.println("六种状态: NEW -> RUNNABLE <-> BLOCKED(等 monitor) / WAITING(wait/join/park) / TIMED_WAITING(sleep/wait(t)) -> TERMINATED");
    }

    static void submissionFlow() throws InterruptedException {
        System.out.println("\n== 任务提交流程 ==");
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread th = new Thread(r, "worker-" + seq.incrementAndGet());
            th.setDaemon(false);
            return th;
        };
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2,                               // corePoolSize
                4,                               // maximumPoolSize
                60, TimeUnit.SECONDS,            // 非核心线程空闲存活时间
                new ArrayBlockingQueue<>(2),     // 有界队列
                factory,
                (r, executor) -> System.out.println("  任务被拒绝（CallerRunsPolicy/AbortPolicy/DiscardPolicy/DiscardOldestPolicy 可选）"));

        for (int i = 1; i <= 7; i++) {
            final int id = i;
            try {
                pool.execute(() -> {
                    System.out.printf("  任务%d 由 %s 执行%n", id, Thread.currentThread().getName());
                    sleep(300);
                });
            } catch (Exception e) {
                System.out.println("  任务" + id + " 抛出 " + e.getClass().getSimpleName());
            }
            System.out.printf("  提交任务%d 后: 线程数=%d 队列=%d%n", id, pool.getPoolSize(), pool.getQueue().size());
        }
        System.out.println("流程：核心线程未满 -> 新建核心线程；满了 -> 入队；队列满 -> 新建非核心线程直到 max；仍满 -> 拒绝策略");
        System.out.println("所以 7 个任务 = 2 核心 + 2 队列 + 2 非核心 + 1 被拒绝");
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    static void futureAndShutdown() throws InterruptedException, ExecutionException {
        System.out.println("\n== Future 与优雅关闭 ==");
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            final int n = i;
            Callable<Integer> task = () -> {
                sleep(100);
                return n * n;
            };
            futures.add(pool.submit(task));
        }
        int sum = 0;
        for (Future<Integer> f : futures) sum += f.get(); // 阻塞直到完成
        System.out.println("平方和 = " + sum + "（Future.get 会抛 ExecutionException 包装任务中的异常；execute 的异常只会打印到控制台）");

        pool.shutdown(); // 不再接收新任务，已提交的继续执行
        if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
            pool.shutdownNow(); // 中断正在执行的任务并返回队列中未执行的任务
        }
        System.out.println("isTerminated = " + pool.isTerminated());
        System.out.println("""
                不推荐 Executors 工厂方法的原因：
                  newFixedThreadPool / newSingleThreadExecutor 使用无界 LinkedBlockingQueue -> 任务堆积 OOM
                  newCachedThreadPool 最大线程数 Integer.MAX_VALUE -> 线程爆炸
                线程数经验公式：CPU 密集 ≈ 核数 + 1；IO 密集 ≈ 核数 * (1 + 等待时间 / 计算时间)，最终以压测为准
                """);
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
