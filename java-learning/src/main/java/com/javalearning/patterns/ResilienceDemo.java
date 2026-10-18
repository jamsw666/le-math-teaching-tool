package com.javalearning.patterns;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 10-7 高并发：限流 / 熔断 / 降级（+ 隔离、超时、重试）。
 *
 * <p>手写四种限流器（固定窗口、滑动窗口、令牌桶、漏桶）并对比临界突刺问题；
 * 三态熔断器（CLOSED -> OPEN -> HALF_OPEN）配合降级回调；舱壁隔离（Semaphore）；
 * 带指数退避 + 抖动的重试。都是 Sentinel / Resilience4j / Hystrix 的核心算法。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.patterns.ResilienceDemo
 */
public class ResilienceDemo {

    public static void main(String[] args) throws Exception {
        rateLimiters();
        circuitBreaker();
        bulkhead();
        retryWithBackoff();
        printSummary();
    }

    // ================= 限流 =================
    interface RateLimiter {
        boolean tryAcquire(long nowMs);
    }

    /** 固定窗口：实现最简单，但窗口边界可能放过 2 倍流量。 */
    static class FixedWindow implements RateLimiter {
        private final int limit;
        private final long windowMs;
        private long windowStart;
        private int count;
        FixedWindow(int limit, long windowMs) { this.limit = limit; this.windowMs = windowMs; }
        public synchronized boolean tryAcquire(long now) {
            if (now - windowStart >= windowMs) { windowStart = now; count = 0; }
            if (count < limit) { count++; return true; }
            return false;
        }
    }

    /** 滑动窗口（日志法）：记录每个请求时间戳，统计最近 windowMs 内的数量。精确但占内存；Sentinel 用分桶近似。 */
    static class SlidingWindow implements RateLimiter {
        private final int limit;
        private final long windowMs;
        private final Deque<Long> log = new ArrayDeque<>();
        SlidingWindow(int limit, long windowMs) { this.limit = limit; this.windowMs = windowMs; }
        public synchronized boolean tryAcquire(long now) {
            while (!log.isEmpty() && now - log.peekFirst() >= windowMs) log.pollFirst();
            if (log.size() < limit) { log.addLast(now); return true; }
            return false;
        }
    }

    /** 令牌桶：匀速放令牌、桶有容量 -> 允许一定突发。Guava RateLimiter / Sentinel 默认。 */
    static class TokenBucket implements RateLimiter {
        private final double capacity;
        private final double refillPerMs;
        private double tokens;
        private long last;
        TokenBucket(int capacity, double perSecond) { this.capacity = capacity; this.tokens = capacity; this.refillPerMs = perSecond / 1000.0; }
        public synchronized boolean tryAcquire(long now) {
            if (last == 0) last = now;
            tokens = Math.min(capacity, tokens + (now - last) * refillPerMs);
            last = now;
            if (tokens >= 1) { tokens -= 1; return true; }
            return false;
        }
    }

    /** 漏桶：以恒定速率“漏出”处理，桶满则拒绝 -> 流量整形，绝对平滑但不允许突发。 */
    static class LeakyBucket implements RateLimiter {
        private final int capacity;
        private final double leakPerMs;
        private double water;
        private long last;
        LeakyBucket(int capacity, double perSecond) { this.capacity = capacity; this.leakPerMs = perSecond / 1000.0; }
        public synchronized boolean tryAcquire(long now) {
            if (last == 0) last = now;
            water = Math.max(0, water - (now - last) * leakPerMs);
            last = now;
            if (water + 1 <= capacity) { water += 1; return true; }
            return false;
        }
    }

    static void rateLimiters() {
        System.out.println("== 限流：限制 10 次/秒，模拟“窗口边界突刺”：第 900~1100ms 之间打 20 个请求 ==");
        record Case(String name, RateLimiter rl) { }
        for (Case c : new Case[]{
                new Case("固定窗口", new FixedWindow(10, 1000)),
                new Case("滑动窗口", new SlidingWindow(10, 1000)),
                new Case("令牌桶  ", new TokenBucket(10, 10)),
                new Case("漏桶    ", new LeakyBucket(10, 10))}) {
            int passed = 0;
            long base = 0;
            for (int i = 0; i < 20; i++) {
                long t = base + 900 + i * 10L; // 900ms..1090ms，横跨 1000ms 边界
                if (c.rl().tryAcquire(t)) passed++;
            }
            System.out.printf("  %s 200ms 内放行 %2d 个（理想 ≤ 10~12）%n", c.name(), passed);
        }
        System.out.println("  固定窗口在边界两侧各放 10 个 -> 瞬时 2 倍；滑动窗口精确；令牌桶初始满桶允许一次突发后按速率；漏桶最平滑");
        System.out.println("  分布式限流：Redis + Lua 原子实现滑动窗口/令牌桶；网关层限流（Gateway RequestRateLimiter）；Sentinel 集群流控");
    }

    // ================= 熔断 =================
    static class CircuitBreaker {
        enum State { CLOSED, OPEN, HALF_OPEN }
        private State state = State.CLOSED;
        private final int failureThreshold;
        private final long openMs;
        private final int halfOpenTrials;
        private int consecutiveFailures;
        private long openedAt;
        private int halfOpenSuccess;

        CircuitBreaker(int failureThreshold, long openMs, int halfOpenTrials) {
            this.failureThreshold = failureThreshold; this.openMs = openMs; this.halfOpenTrials = halfOpenTrials;
        }

        synchronized <T> T call(Supplier<T> action, Supplier<T> fallback) {
            if (state == State.OPEN) {
                if (System.currentTimeMillis() - openedAt >= openMs) {
                    state = State.HALF_OPEN; halfOpenSuccess = 0;
                    System.out.println("      [熔断器] OPEN -> HALF_OPEN，放一个探测请求");
                } else {
                    return fallback.get(); // 快速失败，不打下游
                }
            }
            try {
                T r = action.get();
                onSuccess();
                return r;
            } catch (RuntimeException e) {
                onFailure();
                return fallback.get();
            }
        }

        private void onSuccess() {
            if (state == State.HALF_OPEN && ++halfOpenSuccess >= halfOpenTrials) {
                state = State.CLOSED;
                System.out.println("      [熔断器] 探测成功 -> CLOSED，恢复正常");
            }
            consecutiveFailures = 0;
        }

        private void onFailure() {
            consecutiveFailures++;
            if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
                state = State.OPEN; openedAt = System.currentTimeMillis();
                System.out.println("      [熔断器] 连续失败 " + consecutiveFailures + " 次 -> OPEN，" + openMs + "ms 内直接走降级");
            }
        }

        State state() { return state; }
    }

    static void circuitBreaker() throws InterruptedException {
        System.out.println("\n== 熔断 + 降级 ==");
        AtomicInteger downstreamCalls = new AtomicInteger();
        AtomicInteger phase = new AtomicInteger(0); // 0 = 下游故障，1 = 恢复
        Supplier<String> downstream = () -> {
            downstreamCalls.incrementAndGet();
            if (phase.get() == 0) throw new IllegalStateException("下游超时");
            return "实时推荐列表";
        };
        Supplier<String> fallback = () -> "默认热门榜单（降级）";
        CircuitBreaker cb = new CircuitBreaker(3, 300, 1);

        for (int i = 1; i <= 6; i++) {
            System.out.printf("  请求 %d -> %s  [state=%s]%n", i, cb.call(downstream, fallback), cb.state());
        }
        System.out.println("  下游被真正调用 " + downstreamCalls.get() + " 次（熔断后 3 次请求没有打到下游，保护了下游也保护了自己线程不被拖死）");
        phase.set(1);
        Thread.sleep(350);
        System.out.println("  下游恢复，等待 350ms 后:");
        for (int i = 7; i <= 8; i++) {
            System.out.printf("  请求 %d -> %s  [state=%s]%n", i, cb.call(downstream, fallback), cb.state());
        }
        System.out.println("  生产实现按滑动窗口的失败率/慢调用比例触发（Resilience4j failureRateThreshold=50%），而非简单连续计数");
        System.out.println("  降级策略：返回默认值/缓存值、关闭非核心功能（评论、推荐）、排队页面、只读模式；需要预案与开关（配置中心动态下发）");
    }

    // ================= 舱壁隔离 =================
    static void bulkhead() throws InterruptedException {
        System.out.println("\n== 舱壁隔离：给慢依赖单独的并发配额，不拖垮整个线程池 ==");
        Semaphore slowServiceQuota = new Semaphore(3); // 最多 3 个并发调用慢服务
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger served = new AtomicInteger();
        Thread[] ts = new Thread[10];
        for (int i = 0; i < 10; i++) {
            ts[i] = new Thread(() -> {
                if (slowServiceQuota.tryAcquire()) {
                    try { Thread.sleep(100); served.incrementAndGet(); } catch (InterruptedException ignored) { }
                    finally { slowServiceQuota.release(); }
                } else {
                    rejected.incrementAndGet(); // 立即失败/降级，而不是排队占着 Tomcat 线程
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        System.out.println("  10 个并发请求：" + served.get() + " 个进入慢服务，" + rejected.get() + " 个被快速拒绝，Web 容器线程立即释放");
        System.out.println("  Hystrix 用独立线程池隔离（开销大但可超时中断），Resilience4j / Sentinel 用信号量（轻量）；按依赖分池，一个依赖雪崩不传染");
    }

    // ================= 重试 =================
    static void retryWithBackoff() {
        System.out.println("\n== 重试：指数退避 + 抖动 ==");
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> flaky = () -> {
            if (attempts.incrementAndGet() < 4) throw new IllegalStateException("连接重置");
            return "成功";
        };
        long base = 100;
        AtomicLong totalWait = new AtomicLong();
        String result = null;
        for (int i = 0; i < 5; i++) {
            try {
                result = flaky.get();
                break;
            } catch (RuntimeException e) {
                long backoff = base << i; // 100, 200, 400, 800
                long jitter = ThreadLocalRandom.current().nextLong(backoff / 2); // 抖动避免“重试风暴”同时打到下游
                long wait = backoff + jitter;
                totalWait.addAndGet(wait);
                System.out.printf("  第 %d 次失败(%s)，等待 %d ms 后重试%n", i + 1, e.getMessage(), wait);
            }
        }
        System.out.println("  结果: " + result + "，共尝试 " + attempts.get() + " 次，累计等待 " + totalWait.get() + " ms（示例未真正 sleep）");
        System.out.println("  重试前提：操作幂等！只重试瞬时错误（超时、503、连接重置），不重试业务错误（400、余额不足）；设置最大次数与总超时；配合熔断防止重试放大故障");
    }

    static void printSummary() {
        System.out.println("""

                高并发防护体系（从外到内）：
                  CDN / 静态化 -> 网关限流（按 IP / 用户 / 接口）-> 服务限流（Sentinel QPS / 并发线程数 / 热点参数）-> 隔离（线程池 / 信号量）
                  -> 超时（每个远程调用必须有）-> 重试（幂等 + 退避）-> 熔断 -> 降级 -> 兜底（缓存 / 默认值 / 排队）
                雪崩链：一个慢依赖 -> 调用方线程耗尽 -> 调用方也变慢 -> 上游线程耗尽 -> 全站不可用。每一道防线都是在切断这条链
                秒杀场景组合拳：前端按钮防抖 + 答题/验证码削峰 + 网关限流 + Redis 预扣库存（Lua 原子）+ MQ 异步下单 + DB 乐观锁兜底 + 独立集群部署
                容量规划：压测（JMeter / Gatling / 全链路压测）得到单机 QPS 上限 -> 按峰值 x 冗余系数（1.5~2）定实例数 -> 限流阈值设在压测拐点之下
                可观测性是前提：QPS / RT P99 / 错误率 / 饱和度（Google 四个黄金指标），指标（Prometheus）+ 日志（ELK）+ 链路（SkyWalking / OTel）
                参考：alibaba/Sentinel、resilience4j/resilience4j、Netflix/Hystrix（归档，设计文档仍值得读）、Google SRE Book 第 21/22 章（过载处理、级联故障）
                """);
    }
}
