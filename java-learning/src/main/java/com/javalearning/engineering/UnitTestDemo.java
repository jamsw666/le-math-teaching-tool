package com.javalearning.engineering;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 9-3 单元测试：JUnit 5 / Mockito。
 *
 * <p>本类包含：一个“可测试”设计的 {@link OrderService}（依赖通过接口注入）、
 * 一个 30 行的 MiniJUnit（反射发现 @Test 方法并执行、统计）、一个 40 行的 MiniMock
 * （JDK 动态代理记录调用 + 打桩 + 验证），用来理解 JUnit 与 Mockito 在做什么。
 * 真实的 JUnit 5 + Mockito 测试见 src/test/java/com/javalearning/engineering/OrderServiceTest.java，
 * 运行 mvn test 即可。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.engineering.UnitTestDemo
 */
public class UnitTestDemo {

    // ---------------- 被测代码：依赖倒置，便于替换 ----------------
    public record Product(String sku, BigDecimal price, int stock) { }

    public interface ProductRepository {
        Optional<Product> findBySku(String sku);
        void save(Product product);
    }

    public interface PaymentGateway {
        boolean charge(String userId, BigDecimal amount);
    }

    public static class OrderService {
        private final ProductRepository repo;
        private final PaymentGateway payment;

        public OrderService(ProductRepository repo, PaymentGateway payment) {
            this.repo = repo;
            this.payment = payment;
        }

        public BigDecimal placeOrder(String userId, String sku, int qty) {
            if (qty <= 0) {
                throw new IllegalArgumentException("qty must be positive");
            }
            Product p = repo.findBySku(sku).orElseThrow(() -> new IllegalArgumentException("unknown sku " + sku));
            if (p.stock() < qty) {
                throw new IllegalStateException("insufficient stock");
            }
            BigDecimal total = p.price().multiply(BigDecimal.valueOf(qty));
            if (qty >= 10) {
                total = total.multiply(new BigDecimal("0.9")); // 满 10 件九折
            }
            if (!payment.charge(userId, total)) {
                throw new IllegalStateException("payment failed");
            }
            repo.save(new Product(p.sku(), p.price(), p.stock() - qty));
            return total;
        }
    }

    // ---------------- MiniJUnit ----------------
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Test { }

    static void runTests(Class<?> testClass) throws Exception {
        int passed = 0;
        int failed = 0;
        for (Method m : testClass.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(Test.class)) continue;
            Object instance = testClass.getDeclaredConstructor().newInstance(); // JUnit 默认每个测试方法新建实例 -> 测试间隔离
            try {
                m.invoke(instance);
                passed++;
                System.out.println("    ✔ " + m.getName());
            } catch (InvocationTargetException e) {
                failed++;
                System.out.println("    ✘ " + m.getName() + " -> " + e.getCause());
            }
        }
        System.out.printf("  结果: %d passed, %d failed%n", passed, failed);
    }

    static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static <T extends Throwable> T assertThrows(Class<T> type, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }

    // ---------------- MiniMock ----------------
    /** 用动态代理生成接口的假实现：记录每次调用，按“方法名+参数”返回预设值。 */
    static class MiniMock {
        private final Map<String, Object> stubs = new HashMap<>();
        private final List<String> invocations = new ArrayList<>();

        @SuppressWarnings("unchecked")
        <T> T mock(Class<T> type) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                String key = key(method.getName(), args);
                invocations.add(key);
                if (stubs.containsKey(key)) return stubs.get(key);
                // 未打桩：返回“空”默认值（Mockito 的 RETURNS_DEFAULTS）
                Class<?> rt = method.getReturnType();
                if (rt == boolean.class) return false;
                if (rt == Optional.class) return Optional.empty();
                if (rt.isPrimitive()) return 0;
                return null;
            });
        }

        void when(String method, Object[] args, Object thenReturn) { stubs.put(key(method, args), thenReturn); }

        void verify(String method, Object... args) {
            String key = key(method, args);
            if (!invocations.contains(key)) {
                throw new AssertionError("Wanted but not invoked: " + key + "\n  actual invocations: " + invocations);
            }
        }

        int times(String method) { return (int) invocations.stream().filter(i -> i.startsWith(method + "(")).count(); }

        private static String key(String m, Object[] args) { return m + Arrays.toString(args == null ? new Object[0] : args); }
    }

    // ---------------- 用 MiniJUnit + MiniMock 写的测试 ----------------
    public static class OrderServiceMiniTest {
        final MiniMock mocks = new MiniMock();
        final ProductRepository repo = mocks.mock(ProductRepository.class);
        final PaymentGateway payment = mocks.mock(PaymentGateway.class);
        final OrderService service = new OrderService(repo, payment);

        public OrderServiceMiniTest() {
            // Arrange：打桩
            mocks.when("findBySku", new Object[]{"A1"}, Optional.of(new Product("A1", new BigDecimal("10.00"), 100)));
            mocks.when("charge", new Object[]{"u1", new BigDecimal("100.00")}, true);
            mocks.when("charge", new Object[]{"u1", new BigDecimal("90.000")}, true);
        }

        @Test
        void normalOrder() {
            BigDecimal total = service.placeOrder("u1", "A1", 10); // Act
            assertEquals(new BigDecimal("90.000"), total); // Assert：满 10 件九折
            mocks.verify("save", new Product("A1", new BigDecimal("10.00"), 90)); // 验证副作用
        }

        @Test
        void unknownSku() {
            var e = assertThrows(IllegalArgumentException.class, () -> service.placeOrder("u1", "ZZ", 1));
            assertEquals("unknown sku ZZ", e.getMessage());
            assertEquals(0, mocks.times("charge")); // 没查到商品不应扣款
        }

        @Test
        void paymentFailed() {
            // charge("u1", 50.00) 未打桩 -> 返回 false -> 应抛异常且不扣库存
            assertThrows(IllegalStateException.class, () -> service.placeOrder("u1", "A1", 5));
            assertEquals(0, mocks.times("save"));
        }

        @Test
        void deliberatelyFailing() {
            assertEquals(new BigDecimal("1"), service.placeOrder("u1", "A1", 10)); // 故意写错，看失败输出长什么样
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== MiniJUnit 运行 OrderServiceMiniTest ==");
        runTests(OrderServiceMiniTest.class);
        System.out.println("""

                JUnit 5 架构：Platform（启动器 + 引擎 API，IDE/Maven 通过它运行）+ Jupiter（新编程模型与引擎）+ Vintage（跑 JUnit 4）
                生命周期：@BeforeAll(static) -> [@BeforeEach -> @Test -> @AfterEach]* -> @AfterAll；默认 PER_METHOD 每个方法新实例
                常用注解：@DisplayName @Nested @ParameterizedTest(@ValueSource/@CsvSource/@MethodSource) @RepeatedTest @Timeout @Disabled @Tag
                断言：assertEquals / assertThrows / assertAll（聚合多个断言）/ assertTimeout；更流畅可用 AssertJ: assertThat(x).isEqualTo(y)

                Mockito 对应关系（见 src/test 下的真实测试）：
                  本例 MiniMock.mock(type)                  -> @Mock / Mockito.mock(type)（底层用 ByteBuddy 生成子类，可 mock 类而不止接口）
                  本例 when(method, args, ret)              -> when(repo.findBySku("A1")).thenReturn(Optional.of(p))  /  thenThrow / thenAnswer
                  本例 verify(method, args) / times()       -> verify(repo).save(any())  /  verify(payment, never()).charge(any(), any())
                  参数匹配 any() / eq() / argThat()；ArgumentCaptor 捕获参数再断言；@InjectMocks 自动把 mock 注入被测对象；@Spy 部分真实
                  static / final / private 方法：Mockito 5 默认 inline mock maker 可以 mock final 与 static（mockStatic），但这通常是设计有问题的信号

                好的单元测试：F.I.R.S.T（Fast 快、Independent 独立、Repeatable 可重复、Self-validating 自校验、Timely 及时）
                  - 一个测试只验证一个行为，命名说明场景与期望：placeOrder_whenStockInsufficient_throws
                  - Arrange / Act / Assert 三段式（Given / When / Then）
                  - 只 mock 外部依赖（DB、HTTP、时间、随机），不要 mock 被测类本身的值对象
                  - 测试金字塔：大量单元测试 + 适量集成测试（Testcontainers 起真实 MySQL/Redis 容器、@SpringBootTest）+ 少量端到端
                  - 覆盖率（JaCoCo）是下限不是目标；变异测试（PIT）检验测试是否真能发现 bug
                参考：junit-team/junit5、mockito/mockito、assertj/assertj、testcontainers/testcontainers-java
                """);
    }
}
