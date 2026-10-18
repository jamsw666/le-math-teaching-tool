package com.javalearning.concurrency;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 知识点 3-8：CompletableFuture 与 ForkJoin。
 *
 * <p>重点：异步编排（thenApply / thenCompose / thenCombine / allOf / anyOf）、异常处理（exceptionally / handle）、
 * 超时（orTimeout / completeOnTimeout，Java 9）、指定线程池避免阻塞 commonPool、ForkJoin 的分治与工作窃取。
 */
public class CompletableFutureDemo {

    record UserInfo(String name) { }
    record OrderSummary(int count) { }

    static final ExecutorService IO_POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "io-pool");
        t.setDaemon(true);
        return t;
    });

    static CompletableFuture<UserInfo> fetchUser(long id) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(100);
            return new UserInfo("user-" + id);
        }, IO_POOL);
    }

    static CompletableFuture<OrderSummary> fetchOrders(long id) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(150);
            return new OrderSummary((int) (id * 3));
        }, IO_POOL);
    }

    /** ForkJoin：递归拆分求和，阈值以下直接计算。 */
    static class SumTask extends RecursiveTask<Long> {
        private final long[] data;
        private final int from, to;
        static final int THRESHOLD = 10_000;

        SumTask(long[] data, int from, int to) {
            this.data = data;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Long compute() {
            if (to - from <= THRESHOLD) {
                long s = 0;
                for (int i = from; i < to; i++) s += data[i];
                return s;
            }
            int mid = (from + to) >>> 1;
            SumTask left = new SumTask(data, from, mid);
            SumTask right = new SumTask(data, mid, to);
            left.fork();                        // 异步提交左半部分（可能被其他线程窃取）
            return right.compute() + left.join(); // 当前线程算右半部分，再等左半部分
        }
    }

    public static void main(String[] args) throws Exception {
        composition();
        errorHandlingAndTimeout();
        forkJoin();
    }

    static void composition() throws Exception {
        System.out.println("== 编排 ==");
        long start = System.currentTimeMillis();
        // 并行调用两个远程服务，再合并结果
        CompletableFuture<String> page = fetchUser(7)
                .thenCombine(fetchOrders(7), (user, orders) -> user.name() + " 有 " + orders.count() + " 个订单")
                .thenApply(String::toUpperCase);
        System.out.println("  thenCombine 并行合并: " + page.get() + "，耗时 " + (System.currentTimeMillis() - start) + " ms（≈ max 而非 sum）");

        // 串行依赖：先查用户再根据用户查订单 -> thenCompose 扁平化，避免 CompletableFuture<CompletableFuture<..>>
        CompletableFuture<OrderSummary> chained = fetchUser(1).thenCompose(u -> fetchOrders(u.name().length()));
        System.out.println("  thenCompose 串行依赖: " + chained.get());

        List<CompletableFuture<UserInfo>> batch = List.of(fetchUser(1), fetchUser(2), fetchUser(3));
        CompletableFuture<List<String>> all = CompletableFuture.allOf(batch.toArray(new CompletableFuture[0]))
                .thenApply(v -> batch.stream().map(f -> f.join().name()).toList());
        System.out.println("  allOf 等待全部: " + all.get());
        Object first = CompletableFuture.anyOf(fetchOrders(1), fetchUser(2)).get();
        System.out.println("  anyOf 取最快: " + first);
        System.out.println("  thenApply 在完成线程执行；thenApplyAsync 切换到指定池。不传池默认 ForkJoinPool.commonPool，IO 任务请自带线程池");
    }

    static void errorHandlingAndTimeout() throws Exception {
        System.out.println("\n== 异常与超时 ==");
        CompletableFuture<Integer> failing = CompletableFuture.<Integer>supplyAsync(() -> {
            throw new IllegalStateException("下游服务不可用");
        }).exceptionally(ex -> {
            System.out.println("  exceptionally 捕获: " + ex.getCause().getMessage() + "，返回降级值");
            return -1;
        });
        System.out.println("  结果 = " + failing.get());

        String handled = CompletableFuture.supplyAsync(() -> "ok")
                .handle((v, ex) -> ex == null ? "成功:" + v : "失败:" + ex.getMessage())
                .get();
        System.out.println("  handle 同时处理正常与异常分支: " + handled);

        try {
            CompletableFuture.supplyAsync(() -> {
                sleep(500);
                return "slow";
            }).orTimeout(100, TimeUnit.MILLISECONDS).get();
        } catch (Exception e) {
            System.out.println("  orTimeout 100ms -> " + (e.getCause() instanceof TimeoutException ? "TimeoutException" : e.getCause()));
        }
        String fallback = CompletableFuture.supplyAsync(() -> {
            sleep(500);
            return "slow";
        }).completeOnTimeout("默认值", 100, TimeUnit.MILLISECONDS).get();
        System.out.println("  completeOnTimeout -> " + fallback);
    }

    static void forkJoin() {
        System.out.println("\n== ForkJoin 分治 ==");
        long[] data = new long[2_000_000];
        for (int i = 0; i < data.length; i++) data[i] = i;
        ForkJoinPool pool = ForkJoinPool.commonPool();
        long t = System.nanoTime();
        long sum = pool.invoke(new SumTask(data, 0, data.length));
        System.out.printf("  ForkJoin 求和 = %d，耗时 %.1f ms，并行度 %d%n", sum, (System.nanoTime() - t) / 1e6, pool.getParallelism());
        System.out.println("  每个工作线程有自己的双端队列，空闲线程从别人队尾\"窃取\"任务（work-stealing）；parallelStream 底层就是它");
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
