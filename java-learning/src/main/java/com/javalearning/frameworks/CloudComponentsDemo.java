package com.javalearning.frameworks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 知识点 7-4：Spring Cloud 微服务组件。
 *
 * <p>用进程内的迷你实现串起微服务的几个基础设施：注册中心（服务注册/发现/心跳剔除）、客户端负载均衡（轮询）、
 * 声明式调用（OpenFeign 思路）、熔断器（Closed → Open → Half-Open）、API 网关（路由 + 过滤）。
 * 对应 Nacos/Eureka、LoadBalancer、OpenFeign、Resilience4j/Sentinel、Spring Cloud Gateway。
 */
public class CloudComponentsDemo {

    record Instance(String service, String host, int port) {
        String url() { return host + ":" + port; }
    }

    /** 注册中心：Nacos / Eureka / Consul 的核心就是一个带心跳的 Map。 */
    static class Registry {
        private final Map<String, List<Instance>> services = new ConcurrentHashMap<>();
        private final Map<Instance, Long> lastHeartbeat = new ConcurrentHashMap<>();

        void register(Instance i) {
            services.computeIfAbsent(i.service(), k -> new CopyOnWriteArrayList<>()).add(i);
            heartbeat(i);
            System.out.println("  [注册中心] " + i.service() + " 注册实例 " + i.url());
        }

        void heartbeat(Instance i) { lastHeartbeat.put(i, System.currentTimeMillis()); }

        void evictExpired(long ttlMs) {
            long now = System.currentTimeMillis();
            lastHeartbeat.forEach((i, t) -> {
                if (now - t > ttlMs) {
                    services.get(i.service()).remove(i);
                    lastHeartbeat.remove(i);
                    System.out.println("  [注册中心] 心跳超时，剔除 " + i.url());
                }
            });
        }

        List<Instance> discover(String service) { return services.getOrDefault(service, List.of()); }
    }

    /** 客户端负载均衡：Spring Cloud LoadBalancer 默认轮询。 */
    static class RoundRobinLoadBalancer {
        private final AtomicInteger counter = new AtomicInteger();

        Instance choose(List<Instance> instances) {
            if (instances.isEmpty()) throw new IllegalStateException("没有可用实例");
            return instances.get(Math.floorMod(counter.getAndIncrement(), instances.size()));
        }
    }

    /** 熔断器三态状态机（Resilience4j / Hystrix / Sentinel 的核心）。 */
    static class CircuitBreaker {
        enum State { CLOSED, OPEN, HALF_OPEN }
        private State state = State.CLOSED;
        private int failures = 0;
        private long openedAt;
        private final int threshold;
        private final long openDurationMs;

        CircuitBreaker(int threshold, long openDurationMs) {
            this.threshold = threshold;
            this.openDurationMs = openDurationMs;
        }

        synchronized <T> T call(Supplier<T> action, Supplier<T> fallback) {
            if (state == State.OPEN) {
                if (System.currentTimeMillis() - openedAt >= openDurationMs) {
                    state = State.HALF_OPEN;                   // 放一个请求试探
                    System.out.println("  [熔断器] OPEN -> HALF_OPEN 试探");
                } else {
                    return fallback.get();                     // 快速失败，不打下游
                }
            }
            try {
                T result = action.get();
                if (state == State.HALF_OPEN) System.out.println("  [熔断器] 试探成功 HALF_OPEN -> CLOSED");
                state = State.CLOSED;
                failures = 0;
                return result;
            } catch (RuntimeException e) {
                failures++;
                if (state == State.HALF_OPEN || failures >= threshold) {
                    state = State.OPEN;
                    openedAt = System.currentTimeMillis();
                    System.out.println("  [熔断器] 连续失败 " + failures + " 次 -> OPEN");
                }
                return fallback.get();
            }
        }
    }

    /** 模拟远程调用：9002 实例总是失败。 */
    static String remoteCall(Instance i, String path) {
        if (i.port() == 9002) throw new IllegalStateException("实例 " + i.url() + " 超时");
        return "来自 " + i.url() + " 的响应 " + path;
    }

    /** 网关：路由 + 过滤器链（鉴权、限流、日志）。 */
    static class Gateway {
        record Route(String prefix, String service) { }
        private final List<Route> routes = new ArrayList<>();
        private final Registry registry;
        private final RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer();

        Gateway(Registry registry) { this.registry = registry; }
        Gateway route(String prefix, String service) { routes.add(new Route(prefix, service)); return this; }

        String handle(String path, String token) {
            if (token == null) return "401 网关拦截：缺少 token";
            Route route = routes.stream().filter(r -> path.startsWith(r.prefix())).findFirst().orElse(null);
            if (route == null) return "404 网关：无匹配路由";
            Instance target = lb.choose(registry.discover(route.service()));
            return "网关转发 " + path + " -> " + route.service() + "@" + target.url();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== 服务注册与发现 ==");
        Registry registry = new Registry();
        for (int port : new int[]{9001, 9002, 9003}) registry.register(new Instance("order-service", "10.0.0.1", port));
        registry.register(new Instance("user-service", "10.0.0.2", 8001));

        System.out.println("\n== 负载均衡 + 熔断（OpenFeign 客户端视角） ==");
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer();
        CircuitBreaker breaker = new CircuitBreaker(2, 300);
        for (int i = 1; i <= 6; i++) {
            Instance chosen = lb.choose(registry.discover("order-service"));
            String result = breaker.call(() -> remoteCall(chosen, "/orders/" + 1), () -> "降级：返回缓存订单");
            System.out.println("  第" + i + "次 -> " + chosen.url() + " : " + result);
        }
        Thread.sleep(350);
        Instance healthy = registry.discover("order-service").get(0);
        System.out.println("  等待 300ms 后再调 -> " + breaker.call(() -> remoteCall(healthy, "/orders/2"), () -> "降级"));

        System.out.println("\n== 心跳剔除 ==");
        Thread.sleep(120);
        registry.heartbeat(registry.discover("order-service").get(0));
        registry.heartbeat(registry.discover("order-service").get(2));
        registry.heartbeat(registry.discover("user-service").get(0));
        registry.evictExpired(100);
        System.out.println("  order-service 剩余实例: " + registry.discover("order-service").stream().map(Instance::url).toList());

        System.out.println("\n== API 网关 ==");
        Gateway gateway = new Gateway(registry).route("/api/orders", "order-service").route("/api/users", "user-service");
        System.out.println("  " + gateway.handle("/api/orders/1", "jwt-token"));
        System.out.println("  " + gateway.handle("/api/users/1", "jwt-token"));
        System.out.println("  " + gateway.handle("/api/orders/1", null));
        System.out.println("  " + gateway.handle("/api/unknown", "jwt-token"));

        System.out.println("""

                Spring Cloud 组件对照（2023+ 主流选型：Spring Cloud Alibaba）：
                  注册中心 / 配置中心    Nacos（AP+CP 可切换）、Eureka（AP，维护模式）、Consul、Zookeeper
                  客户端负载均衡        Spring Cloud LoadBalancer（Ribbon 已废弃）：轮询、随机、权重、区域亲和
                  声明式 HTTP 客户端    OpenFeign：接口 + 注解 -> 动态代理生成实现，集成 LoadBalancer 与熔断；也可用 Spring 6 的 HTTP Interface
                  熔断 / 限流 / 降级    Sentinel（流控、热点、系统自适应）、Resilience4j（熔断、重试、舱壁、限速）；Hystrix 已停更
                  网关                  Spring Cloud Gateway（WebFlux 响应式，Predicate 路由 + Filter 链）；Zuul 1 已淘汰
                  分布式事务            Seata（AT / TCC / SAGA / XA）
                  链路追踪              Micrometer Tracing + Zipkin/SkyWalking/OpenTelemetry；日志 TraceId 透传
                  消息驱动              Spring Cloud Stream（Binder 抽象 RocketMQ / Kafka / RabbitMQ）
                拆分原则：按业务领域（DDD 限界上下文）而非技术层拆；先单体模块化，再演进为微服务；每个服务独享数据库
                """);
    }
}
