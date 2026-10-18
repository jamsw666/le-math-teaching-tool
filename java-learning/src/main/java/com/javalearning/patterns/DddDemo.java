package com.javalearning.patterns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 10-5 微服务与领域驱动设计（DDD）。
 *
 * <p>用一个“下单”用例对照 贫血模型（Service 里堆逻辑）与 充血模型（聚合根保护不变量），
 * 展示 DDD 战术模式：实体 / 值对象 / 聚合根 / 领域事件 / 仓储 / 应用服务 / 防腐层，
 * 以及战略设计中限界上下文如何映射为微服务边界。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.patterns.DddDemo
 */
public class DddDemo {

    public static void main(String[] args) {
        anemicModel();
        richModel();
        boundedContexts();
        printSummary();
    }

    // ================= 贫血模型 =================
    /** 只有 getter/setter 的“数据袋”，任何人都能把它改成非法状态。 */
    static class AnemicOrder {
        String status = "CREATED";
        List<String[]> lines = new ArrayList<>(); // [sku, qty, price]
        BigDecimal total = BigDecimal.ZERO;
    }

    static class AnemicOrderService {
        void addLine(AnemicOrder o, String sku, int qty, BigDecimal price) {
            // 业务规则散落在 Service，且下一个 Service 可能忘了校验
            if (!o.status.equals("CREATED")) throw new IllegalStateException("已支付订单不能改");
            o.lines.add(new String[]{sku, String.valueOf(qty), price.toString()});
            o.total = o.total.add(price.multiply(BigDecimal.valueOf(qty)));
        }
    }

    static void anemicModel() {
        System.out.println("== 贫血模型 ==");
        AnemicOrder o = new AnemicOrder();
        new AnemicOrderService().addLine(o, "A1", 2, new BigDecimal("10"));
        o.status = "PAID";
        o.total = new BigDecimal("-999"); // 没有任何东西阻止这种事
        o.lines.clear();
        System.out.println("  任何代码都能绕过 Service 直接把订单改成 status=" + o.status + " total=" + o.total + " lines=" + o.lines.size() + "  <- 不变量无人守护");
        System.out.println("  大量 CRUD 系统用贫血模型也没问题；但规则复杂后，规则会在多个 Service 中重复、遗漏、互相冲突");
    }

    // ================= 充血模型（DDD 战术模式） =================
    /** 值对象：不可变、按值相等、自带校验与行为。 */
    record Money(BigDecimal amount, String currency) {
        Money {
            if (amount.signum() < 0) throw new IllegalArgumentException("金额不能为负");
        }
        static Money cny(String v) { return new Money(new BigDecimal(v), "CNY"); }
        Money plus(Money o) { requireSame(o); return new Money(amount.add(o.amount), currency); }
        Money times(int n) { return new Money(amount.multiply(BigDecimal.valueOf(n)), currency); }
        private void requireSame(Money o) {
            if (!currency.equals(o.currency)) throw new IllegalArgumentException("币种不同");
        }
        @Override public String toString() { return amount + " " + currency; }
    }

    record OrderId(String value) {
        static OrderId next() { return new OrderId(UUID.randomUUID().toString().substring(0, 8)); }
    }

    /** 实体（聚合内部）：有标识，生命周期由聚合根管理，外部不能直接拿到。 */
    static final class OrderLine {
        private final String sku;
        private int qty;
        private final Money unitPrice;
        private OrderLine(String sku, int qty, Money unitPrice) { this.sku = sku; this.qty = qty; this.unitPrice = unitPrice; }
        Money subtotal() { return unitPrice.times(qty); }
        String sku() { return sku; }
    }

    /** 领域事件：聚合内发生的、其他上下文关心的事实（过去式命名）。 */
    sealed interface DomainEvent permits OrderPlaced, OrderPaid { }
    record OrderPlaced(OrderId id, Money total) implements DomainEvent { }
    record OrderPaid(OrderId id, Money total) implements DomainEvent { }

    enum Status { CREATED, PAID, CANCELLED }

    /** 聚合根：唯一入口，所有修改都经过它的方法，方法保证不变量成立。 */
    static final class Order {
        private final OrderId id;
        private Status status = Status.CREATED;
        private final List<OrderLine> lines = new ArrayList<>();
        private final List<DomainEvent> events = new ArrayList<>();
        private static final int MAX_LINES = 5;

        private Order(OrderId id) { this.id = id; }

        static Order create() { return new Order(OrderId.next()); }

        void addLine(String sku, int qty, Money price) {
            mustBe(Status.CREATED, "添加商品");
            if (qty <= 0) throw new IllegalArgumentException("数量必须为正");
            if (lines.size() >= MAX_LINES) throw new IllegalStateException("一单最多 " + MAX_LINES + " 种商品");
            lines.stream().filter(l -> l.sku().equals(sku)).findFirst()
                    .ifPresentOrElse(l -> l.qty += qty, () -> lines.add(new OrderLine(sku, qty, price)));
        }

        void place() {
            mustBe(Status.CREATED, "提交");
            if (lines.isEmpty()) throw new IllegalStateException("空订单不能提交");
            events.add(new OrderPlaced(id, total()));
        }

        void pay(Money paid) {
            mustBe(Status.CREATED, "支付");
            if (!paid.equals(total())) throw new IllegalArgumentException("支付金额 " + paid + " 与应付 " + total() + " 不符");
            status = Status.PAID;
            events.add(new OrderPaid(id, paid));
        }

        Money total() {
            return lines.stream().map(OrderLine::subtotal).reduce(Money.cny("0"), Money::plus);
        }

        private void mustBe(Status s, String action) {
            if (status != s) throw new IllegalStateException(status + " 状态不允许" + action);
        }

        OrderId id() { return id; }
        Status status() { return status; }
        /** 只读视图：外部拿不到可变的内部集合。 */
        List<DomainEvent> pullEvents() { List<DomainEvent> out = List.copyOf(events); events.clear(); return out; }
        int lineCount() { return lines.size(); }
    }

    /** 仓储接口属于领域层；实现（JPA / MyBatis / 内存）在基础设施层。以聚合为单位存取。 */
    interface OrderRepository {
        void save(Order order);
        Optional<Order> find(OrderId id);
    }

    static class InMemoryOrderRepository implements OrderRepository {
        private final Map<OrderId, Order> store = new HashMap<>();
        public void save(Order o) { store.put(o.id(), o); }
        public Optional<Order> find(OrderId id) { return Optional.ofNullable(store.get(id)); }
    }

    /** 防腐层：把外部支付系统的模型/异常翻译成本上下文的语言，避免外部概念渗入领域。 */
    interface PaymentGatewayAcl {
        boolean charge(OrderId id, Money amount);
    }

    /** 应用服务：编排用例（事务边界、取聚合、调领域方法、存回、发事件），自身不含业务规则。 */
    static class PlaceOrderAppService {
        private final OrderRepository repo;
        private final PaymentGatewayAcl payment;
        private final List<DomainEvent> publishedEvents = new ArrayList<>();

        PlaceOrderAppService(OrderRepository repo, PaymentGatewayAcl payment) { this.repo = repo; this.payment = payment; }

        OrderId placeAndPay(Map<String, Integer> cart, Map<String, Money> prices) {
            Order order = Order.create();
            cart.forEach((sku, qty) -> order.addLine(sku, qty, prices.get(sku)));
            order.place();
            if (payment.charge(order.id(), order.total())) {
                order.pay(order.total());
            }
            repo.save(order);
            publishedEvents.addAll(order.pullEvents()); // 真实场景：事务提交后发到 MQ（Outbox 模式保证不丢）
            return order.id();
        }
    }

    static void richModel() {
        System.out.println("\n== 充血模型：聚合根守护不变量 ==");
        Map<String, Money> prices = Map.of("A1", Money.cny("10"), "B2", Money.cny("25.5"));
        var repo = new InMemoryOrderRepository();
        var app = new PlaceOrderAppService(repo, (id, amt) -> { System.out.println("      [支付 ACL] 扣款 " + amt); return true; });
        OrderId id = app.placeAndPay(Map.of("A1", 2, "B2", 1), prices);
        Order saved = repo.find(id).orElseThrow();
        System.out.println("  订单 " + id.value() + " 状态=" + saved.status() + " 总额=" + saved.total() + " 行数=" + saved.lineCount());
        System.out.println("  领域事件: " + app.publishedEvents);

        System.out.println("  尝试破坏不变量：");
        for (Runnable attempt : List.<Runnable>of(
                () -> saved.addLine("C3", 1, Money.cny("1")),
                () -> saved.pay(Money.cny("1")),
                () -> Money.cny("-5"),
                () -> { Order o = Order.create(); o.place(); })) {
            try {
                attempt.run();
                System.out.println("      ✘ 居然成功了");
            } catch (RuntimeException e) {
                System.out.println("      ✔ 被拒绝: " + e.getMessage());
            }
        }
        System.out.println("  规则只写在一处（聚合根），任何调用路径都无法绕过；应用服务薄、领域模型厚");
    }

    static void boundedContexts() {
        System.out.println("\n== 战略设计：限界上下文 -> 微服务边界 ==");
        System.out.println("""
                  同一个词在不同上下文含义不同：
                    “商品” 在 商品上下文 = 标题/详情/图片/类目；在 订单上下文 = sku + 快照价格；在 库存上下文 = 仓库 + 可用数量
                    “用户” 在 账号上下文 = 登录凭证；在 营销上下文 = 画像/标签；在 订单上下文 = 收货人 + 地址
                  强行用一个大而全的 Product / User 表服务所有场景，就是“大泥球”
                  上下文映射关系：
                    客户-供应商（上游按下游需求提供 API）| 遵奉者（下游全盘接受上游模型）| 防腐层（下游翻译上游模型，隔离变化）
                    发布语言（共享的事件/DTO 契约）| 共享内核（少量共用代码，慎用）
                  拆分启发：事件风暴（Event Storming）找领域事件 -> 命令 -> 聚合 -> 按高内聚的聚合簇划上下文
                  微服务 = 限界上下文的部署形态；一个上下文可以先是单体里的一个模块（模块化单体），验证边界稳定后再独立部署
                  每个服务独享数据库，跨服务通过 API / 事件协作，不共享表；跨服务一致性用 Saga / 最终一致（见 DistributedDemo）""");
    }

    static void printSummary() {
        System.out.println("""

                分层（整洁/六边形架构）：
                  接口层 Controller / MQ Listener  ->  应用层 AppService（用例编排、事务、权限）  ->  领域层 Entity / ValueObject / Aggregate / DomainService / Repository 接口 / DomainEvent
                                                                                                 <-  基础设施层 Repository 实现 / 外部服务 ACL / MQ 发送
                  依赖只能向内（领域层不依赖任何框架与外层），这正是 DIP 在架构层面的应用
                战术模式速查：
                  实体 有唯一标识、可变 | 值对象 无标识、不可变、按值相等（record 天生合适）| 聚合 一致性边界，根是唯一入口，一个事务只改一个聚合
                  领域服务 跨多个聚合的业务逻辑（转账）| 仓储 以聚合为单位的集合语义 | 工厂 复杂聚合的创建 | 领域事件 解耦聚合与上下文
                  CQRS 读写模型分离（写走聚合，读走宽表/ES）| 事件溯源 用事件序列重建状态
                常见误区：为 CRUD 系统强上 DDD；聚合设计过大（锁冲突、加载慢）；Repository 里写查询拼业务；把 DTO 当领域对象
                参考：《领域驱动设计》（Evans）、《实现领域驱动设计》（Vernon）、alibaba/COLA（应用架构脚手架）、ddd-crew/ddd-starter-modelling-process、citerus/dddsample-core
                """);
    }
}
