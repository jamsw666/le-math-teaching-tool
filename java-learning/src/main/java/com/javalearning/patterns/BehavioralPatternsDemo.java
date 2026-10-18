package com.javalearning.patterns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 10-3 行为型模式：策略 / 观察者 / 模板方法 / 责任链（+ 状态、命令）。
 *
 * <p>策略：用 Map + 函数式接口替代 if-else 分支（Spring 环境下用 Bean 注入 Map 实现）；
 * 观察者：事件发布/订阅，对比 Spring ApplicationEvent；模板方法：固定流程 + 可变步骤；
 * 责任链：请求沿链传递，对比 Servlet Filter / Spring Interceptor / Netty Pipeline；
 * 状态：用状态对象替代 switch，订单状态机；命令：封装请求支持撤销。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.patterns.BehavioralPatternsDemo
 */
public class BehavioralPatternsDemo {

    public static void main(String[] args) {
        strategy();
        observer();
        templateMethod();
        chainOfResponsibility();
        state();
        command();
        printSummary();
    }

    // ---------------- 策略 ----------------
    interface DiscountStrategy {
        BigDecimal apply(BigDecimal price);
    }

    static final Map<String, DiscountStrategy> STRATEGIES = Map.of(
            "NONE", p -> p,
            "VIP", p -> p.multiply(new BigDecimal("0.8")),
            "COUPON_10", p -> p.subtract(BigDecimal.TEN).max(BigDecimal.ZERO),
            "FLASH_SALE", p -> p.multiply(new BigDecimal("0.5")));

    static void strategy() {
        System.out.println("== 策略 ==");
        BigDecimal price = new BigDecimal("100");
        for (String type : List.of("NONE", "VIP", "COUPON_10", "FLASH_SALE")) {
            System.out.printf("  %-10s 100 -> %s%n", type, STRATEGIES.get(type).apply(price));
        }
        System.out.println("  替代 if (vip) ... else if (coupon) ... 的分支膨胀；新增策略只加一个实现不改调用方（开闭原则）");
        System.out.println("  Spring 写法：各策略实现类标 @Component，注入 Map<String, DiscountStrategy>（key = bean 名）即自动收集");
        System.out.println("  JDK 中：Comparator、ThreadPoolExecutor 的 RejectedExecutionHandler、Stream 的各种函数参数");
    }

    // ---------------- 观察者 ----------------
    record OrderPaidEvent(long orderId, BigDecimal amount) { }

    static class EventBus {
        private final Map<Class<?>, List<Consumer<Object>>> listeners = new java.util.HashMap<>();

        @SuppressWarnings("unchecked")
        <E> void subscribe(Class<E> type, Consumer<E> listener) {
            listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add((Consumer<Object>) listener);
        }

        void publish(Object event) {
            for (Consumer<Object> l : listeners.getOrDefault(event.getClass(), List.of())) {
                try {
                    l.accept(event);
                } catch (RuntimeException e) {
                    System.out.println("      监听器异常被隔离: " + e.getMessage()); // 一个监听器失败不影响其他
                }
            }
        }
    }

    static void observer() {
        System.out.println("\n== 观察者 / 发布-订阅 ==");
        EventBus bus = new EventBus();
        bus.subscribe(OrderPaidEvent.class, e -> System.out.println("      [库存服务] 扣减订单 " + e.orderId() + " 的库存"));
        bus.subscribe(OrderPaidEvent.class, e -> { throw new IllegalStateException("积分服务不可用"); });
        bus.subscribe(OrderPaidEvent.class, e -> System.out.println("      [通知服务] 发送支付成功短信，金额 " + e.amount()));
        bus.publish(new OrderPaidEvent(1001, new BigDecimal("99.00")));
        System.out.println("  订单服务只发事件，不知道有谁在听 -> 解耦；新增“发优惠券”只需再订阅一个监听器");
        System.out.println("  Spring：ApplicationEventPublisher.publishEvent + @EventListener（默认同步，@Async 异步，@TransactionalEventListener 事务提交后）");
        System.out.println("  跨进程的观察者就是消息队列；JDK 的 java.util.Observable 已废弃，用 PropertyChangeSupport 或 Flow API");
    }

    // ---------------- 模板方法 ----------------
    static abstract class DataImporter {
        /** 模板方法：final 固定骨架。 */
        final void run(String source) {
            System.out.println("      1. 打开 " + source);
            List<String> rows = parse(source);          // 抽象步骤：子类实现
            System.out.println("      2. 解析得到 " + rows.size() + " 行");
            if (shouldValidate()) {                      // 钩子方法：子类可选覆盖
                System.out.println("      3. 校验通过");
            }
            rows.forEach(r -> System.out.println("      4. 写入 " + transform(r)));
            System.out.println("      5. 关闭资源");
        }
        protected abstract List<String> parse(String source);
        protected String transform(String row) { return row; }
        protected boolean shouldValidate() { return true; }
    }

    static class CsvImporter extends DataImporter {
        protected List<String> parse(String s) { return List.of("a,1", "b,2"); }
        protected String transform(String row) { return "{" + row.replace(",", ":") + "}"; }
    }

    static class JsonImporter extends DataImporter {
        protected List<String> parse(String s) { return List.of("{\"c\":3}"); }
        protected boolean shouldValidate() { return false; }
    }

    static void templateMethod() {
        System.out.println("\n== 模板方法 ==");
        System.out.println("  CSV:");
        new CsvImporter().run("data.csv");
        System.out.println("  JSON（关闭校验钩子）:");
        new JsonImporter().run("data.json");
        System.out.println("  父类定流程、子类填空；JDK 的 AbstractList、AQS（tryAcquire 由子类实现）、Servlet 的 service -> doGet/doPost、Spring JdbcTemplate / 各种 *Template");
        System.out.println("  函数式替代：把可变步骤作为 Function/Supplier 参数传入，避免继承（“回调”风格，如 TransactionTemplate.execute(callback)）");
    }

    // ---------------- 责任链 ----------------
    record Request(String path, String user, Map<String, String> headers) { }

    interface Handler {
        /** 返回 null 表示放行，非 null 表示在此处理并短路。 */
        String handle(Request req, Chain chain);
    }

    /** 链本身用索引递推，和 Servlet FilterChain / OkHttp Interceptor.Chain 一样。 */
    static class Chain {
        private final List<Handler> handlers;
        private final int index;
        Chain(List<Handler> handlers, int index) { this.handlers = handlers; this.index = index; }

        String proceed(Request req) {
            if (index >= handlers.size()) return "200 OK: " + req.path();
            return handlers.get(index).handle(req, new Chain(handlers, index + 1));
        }
    }

    static void chainOfResponsibility() {
        System.out.println("\n== 责任链 ==");
        List<Handler> chain = List.of(
                (req, next) -> { System.out.println("      [限流] 通过"); return next.proceed(req); },
                (req, next) -> req.user() == null ? "401 未登录" : next.proceed(req),
                (req, next) -> req.path().startsWith("/admin") && !req.user().equals("admin") ? "403 禁止" : next.proceed(req),
                (req, next) -> { String r = next.proceed(req); System.out.println("      [日志] " + req.path() + " -> " + r); return r; });
        for (Request req : List.of(
                new Request("/home", "alice", Map.of()),
                new Request("/admin/users", "alice", Map.of()),
                new Request("/home", null, Map.of()))) {
            System.out.println("  " + req.path() + " by " + req.user() + " => " + new Chain(chain, 0).proceed(req));
        }
        System.out.println("  每个处理器只关心自己的职责，可增删排序；Servlet Filter、Spring HandlerInterceptor、Netty ChannelPipeline、Gateway Filter、审批流都是责任链");
    }

    // ---------------- 状态 ----------------
    enum OrderState {
        CREATED {
            OrderState pay() { return PAID; }
            OrderState cancel() { return CANCELLED; }
        },
        PAID {
            OrderState ship() { return SHIPPED; }
            OrderState cancel() { return REFUNDING; }
        },
        SHIPPED {
            OrderState confirm() { return COMPLETED; }
        },
        REFUNDING, COMPLETED, CANCELLED;

        // 默认实现：非法转换抛异常。每个状态只覆盖自己允许的事件 -> 转换规则内聚在状态里
        OrderState pay() { return illegal("pay"); }
        OrderState ship() { return illegal("ship"); }
        OrderState confirm() { return illegal("confirm"); }
        OrderState cancel() { return illegal("cancel"); }
        private OrderState illegal(String event) { throw new IllegalStateException(this + " 状态不允许 " + event); }
    }

    static void state() {
        System.out.println("\n== 状态 ==");
        OrderState s = OrderState.CREATED;
        s = s.pay();
        System.out.println("  CREATED --pay--> " + s);
        s = s.ship();
        System.out.println("  PAID --ship--> " + s);
        try {
            s.cancel();
        } catch (IllegalStateException e) {
            System.out.println("  SHIPPED --cancel--> " + e.getMessage());
        }
        System.out.println("  替代到处写 if (status == X && event == Y) 的散弹式判断；复杂流程用状态机框架 Spring Statemachine / Squirrel / COLA 状态机");
    }

    // ---------------- 命令 ----------------
    interface Command {
        void execute();
        void undo();
    }

    static class Editor {
        final StringBuilder text = new StringBuilder();
        final java.util.Deque<Command> history = new java.util.ArrayDeque<>();

        void run(Command c) { c.execute(); history.push(c); }
        void undo() { if (!history.isEmpty()) history.pop().undo(); }

        Command append(String s) {
            return new Command() {
                public void execute() { text.append(s); }
                public void undo() { text.setLength(text.length() - s.length()); }
            };
        }
    }

    static void command() {
        System.out.println("\n== 命令 ==");
        Editor ed = new Editor();
        ed.run(ed.append("Hello"));
        ed.run(ed.append(", World"));
        System.out.println("  执行两次 append: " + ed.text);
        ed.undo();
        System.out.println("  undo 一次: " + ed.text);
        System.out.println("  把请求封装成对象 -> 可排队、可记录、可撤销、可重放；Runnable/Callable 就是最简命令，线程池是命令的调用者；事务日志 / 事件溯源是命令思想的延伸");
    }

    static void printSummary() {
        System.out.println("""

                选型速查：
                  同一件事多种算法可切换        -> 策略（Map/枚举注册，别写 if-else）
                  一处变化要通知多处且不想耦合  -> 观察者 / 事件（跨进程用 MQ）
                  流程固定、个别步骤不同        -> 模板方法（或回调函数）
                  请求要经过多道可插拔处理      -> 责任链
                  对象行为随状态改变            -> 状态模式 / 状态机
                  请求需要排队、撤销、记录      -> 命令
                  还有：迭代器（Iterable）、访问者（双分派，Java 17 的 sealed + switch 模式匹配可部分替代）、备忘录（快照/撤销）、中介者（聊天室/事件总线）、解释器（规则表达式）
                模式不是目标：先写直白代码，出现重复、分支膨胀、改一处动多处时再重构成模式；Java 8+ 的 lambda 让很多模式“消失”成一个函数参数
                参考：iluwatar/java-design-patterns、Spring 源码中的 *Template / *Resolver / *Interceptor 命名、《重构》
                """);
    }
}
