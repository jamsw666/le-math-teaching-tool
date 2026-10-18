package com.javalearning.frameworks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 知识点 7-6：JPA / Hibernate 核心概念。
 *
 * <p>不依赖 Hibernate，用迷你 EntityManager 演示 ORM 的四个关键机制：
 * 实体生命周期（Transient → Managed → Detached）、一级缓存（同一会话同 id 返回同一对象）、
 * 脏检查（flush 时对比快照自动生成 UPDATE）、延迟加载与 N+1 问题（以及 JOIN FETCH 的解决思路）。
 */
public class JpaConceptsDemo {

    /** @Entity：带 id 的可变对象。 */
    static class Order {
        Long id;
        String status;
        Long customerId;
        Supplier<Customer> customerLoader; // 模拟 Hibernate 代理：首次访问时才查库
        Customer customer;

        Customer getCustomer() {
            if (customer == null && customerLoader != null) customer = customerLoader.get();
            return customer;
        }

        @Override
        public String toString() {
            return "Order{id=" + id + ", status=" + status + "}";
        }
    }

    record Customer(Long id, String name) { }

    /** 模拟数据库表。 */
    static final Map<Long, Map<String, Object>> ORDER_TABLE = new HashMap<>();
    static final Map<Long, Customer> CUSTOMER_TABLE = Map.of(1L, new Customer(1L, "Alice"), 2L, new Customer(2L, "Bob"));
    static int sqlCount = 0;

    static void sql(String s) {
        sqlCount++;
        System.out.println("    [SQL] " + s);
    }

    /** 迷你 EntityManager / Hibernate Session。 */
    static class MiniEntityManager {
        private final Map<Long, Order> persistenceContext = new HashMap<>(); // 一级缓存 / 持久化上下文
        private final Map<Long, Map<String, Object>> snapshots = new HashMap<>(); // 加载时的快照，用于脏检查
        private long nextId = 100;

        void persist(Order o) {
            o.id = nextId++;
            sql("INSERT INTO orders(id, status, customer_id) VALUES (" + o.id + ", '" + o.status + "', " + o.customerId + ")");
            ORDER_TABLE.put(o.id, new HashMap<>(Map.of("status", o.status, "customer_id", o.customerId)));
            persistenceContext.put(o.id, o);
            snapshots.put(o.id, new HashMap<>(Map.of("status", o.status)));
        }

        Order find(long id) {
            Order cached = persistenceContext.get(id);
            if (cached != null) return cached;                  // 一级缓存命中，不发 SQL
            sql("SELECT id, status, customer_id FROM orders WHERE id = " + id);
            Map<String, Object> row = ORDER_TABLE.get(id);
            if (row == null) return null;
            Order o = new Order();
            o.id = id;
            o.status = (String) row.get("status");
            o.customerId = (Long) row.get("customer_id");
            o.customerLoader = () -> {                          // @ManyToOne(fetch = LAZY)
                sql("SELECT id, name FROM customers WHERE id = " + o.customerId);
                return CUSTOMER_TABLE.get(o.customerId);
            };
            persistenceContext.put(id, o);
            snapshots.put(id, new HashMap<>(Map.of("status", o.status)));
            return o;
        }

        List<Order> findAll() {
            sql("SELECT id, status, customer_id FROM orders");
            List<Order> result = new ArrayList<>();
            for (Long id : ORDER_TABLE.keySet()) result.add(find(id));
            return result;
        }

        List<Order> findAllWithCustomer() {
            sql("SELECT o.*, c.* FROM orders o JOIN customers c ON o.customer_id = c.id   -- JOIN FETCH");
            List<Order> result = new ArrayList<>();
            for (Long id : ORDER_TABLE.keySet()) {
                Order o = find(id);
                o.customer = CUSTOMER_TABLE.get(o.customerId); // 一次查出，无需延迟加载
                o.customerLoader = null;
                result.add(o);
            }
            return result;
        }

        /** flush：脏检查——对比快照，只为变化的实体生成 UPDATE。 */
        void flush() {
            for (Order o : persistenceContext.values()) {
                Map<String, Object> snap = snapshots.get(o.id);
                if (!Objects.equals(snap.get("status"), o.status)) {
                    sql("UPDATE orders SET status = '" + o.status + "' WHERE id = " + o.id);
                    ORDER_TABLE.get(o.id).put("status", o.status);
                    snap.put("status", o.status);
                }
            }
        }

        void detach(Order o) { persistenceContext.remove(o.id); snapshots.remove(o.id); }
        void clear() { persistenceContext.clear(); snapshots.clear(); }
    }

    public static void main(String[] args) {
        MiniEntityManager em = new MiniEntityManager();

        System.out.println("== 实体生命周期：Transient -> Managed ==");
        Order o1 = new Order(); o1.status = "NEW"; o1.customerId = 1L;
        Order o2 = new Order(); o2.status = "NEW"; o2.customerId = 2L;
        Order o3 = new Order(); o3.status = "PAID"; o3.customerId = 1L;
        System.out.println("  persist 前 id = " + o1.id + "（瞬时态，和数据库无关）");
        em.persist(o1); em.persist(o2); em.persist(o3);
        System.out.println("  persist 后 id = " + o1.id + "（托管态，受持久化上下文管理）");

        System.out.println("\n== 一级缓存 ==");
        Order a = em.find(o1.id);
        Order b = em.find(o1.id);
        System.out.println("  同一会话两次 find 同 id：是同一对象 " + (a == b) + "，只发了 0 条 SQL（已在上下文中）");

        System.out.println("\n== 脏检查：不调用 update 也会同步 ==");
        a.status = "SHIPPED";   // 只修改托管对象的字段
        em.flush();             // 事务提交时自动 flush
        System.out.println("  数据库中的 status = " + ORDER_TABLE.get(o1.id).get("status"));

        System.out.println("\n== Detached：脱管对象的修改不会同步 ==");
        em.detach(a);
        a.status = "LOST";
        em.flush();
        System.out.println("  数据库中的 status 仍为 " + ORDER_TABLE.get(o1.id).get("status") + "（需要 merge 重新托管）");

        System.out.println("\n== N+1 问题 ==");
        em.clear();
        sqlCount = 0;
        for (Order o : em.findAll()) {
            System.out.println("  " + o + " 的客户: " + o.getCustomer().name());  // 每次访问触发一条 SQL
        }
        System.out.println("  共 " + sqlCount + " 条 SQL（1 条查订单 + N 条查客户）");

        System.out.println("\n== JOIN FETCH 解决 ==");
        em.clear();
        sqlCount = 0;
        for (Order o : em.findAllWithCustomer()) {
            System.out.println("  " + o + " 的客户: " + o.getCustomer().name());
        }
        System.out.println("  共 " + sqlCount + " 条 SQL");

        System.out.println("""

                JPA 是规范（jakarta.persistence），Hibernate 是最流行实现，Spring Data JPA 在其上提供 Repository 抽象
                实体状态：Transient(new) -> persist -> Managed -> detach/close -> Detached -> merge -> Managed；remove -> Removed
                持久化上下文 = 一级缓存 + 脏检查 + 写延迟（flush 时统一发 SQL，可合并）
                关联加载：@ManyToOne 默认 EAGER（建议改 LAZY），@OneToMany 默认 LAZY；LazyInitializationException = 会话关闭后访问懒加载属性
                N+1 解法：JPQL JOIN FETCH / @EntityGraph / @BatchSize / 开启 hibernate.default_batch_fetch_size
                Spring Data JPA：方法名派生查询 findByStatusAndCustomerId、@Query JPQL/原生 SQL、Specification 动态查询、分页 Pageable
                MyBatis vs JPA：MyBatis SQL 可控、适合复杂报表与性能敏感；JPA 开发效率高、适合领域模型清晰的业务；国内互联网偏 MyBatis(-Plus)，国外偏 JPA
                """);
    }
}
