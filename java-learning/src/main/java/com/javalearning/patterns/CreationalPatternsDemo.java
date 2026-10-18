package com.javalearning.patterns;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 10-1 创建型模式：单例 / 工厂 / 建造者。
 *
 * <p>单例：饿汉、DCL（为什么要 volatile）、静态内部类、枚举（如何防反射与反序列化破坏）；
 * 工厂：简单工厂 -> 工厂方法 -> 抽象工厂的演进，以及 JDK 里的例子；
 * 建造者：解决多参数构造的可读性问题，与 Lombok @Builder / 链式 record 的关系；
 * 附：原型模式（clone 的深浅拷贝）。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.patterns.CreationalPatternsDemo
 */
public class CreationalPatternsDemo {

    public static void main(String[] args) throws Exception {
        singletons();
        factories();
        builder();
        prototype();
        printSummary();
    }

    // ---------------- 单例 ----------------
    /** 懒汉 + 双重检查锁。volatile 禁止“分配内存 -> 初始化 -> 赋值引用”被重排为“分配 -> 赋值 -> 初始化”。 */
    static class DclSingleton {
        private static volatile DclSingleton instance;
        private DclSingleton() { }

        static DclSingleton getInstance() {
            if (instance == null) {                      // 第一次检查：已初始化则不加锁
                synchronized (DclSingleton.class) {
                    if (instance == null) {              // 第二次检查：防止多个线程同时通过第一次检查
                        instance = new DclSingleton();
                    }
                }
            }
            return instance;
        }
    }

    /** 静态内部类：利用类加载的懒加载 + JVM 保证 <clinit> 线程安全，写法最简洁。 */
    static class HolderSingleton {
        private HolderSingleton() { }
        private static class Holder {
            static final HolderSingleton INSTANCE = new HolderSingleton();
        }
        static HolderSingleton getInstance() { return Holder.INSTANCE; }
    }

    /** 普通单例可被反射 / 反序列化破坏。 */
    static class FragileSingleton implements Serializable {
        static final FragileSingleton INSTANCE = new FragileSingleton();
        private FragileSingleton() { }
        // 加上这个方法才能防反序列化：返回已有实例而非新建
        // private Object readResolve() { return INSTANCE; }
    }

    /** 枚举单例：JVM 保证枚举不能被反射实例化（Constructor.newInstance 抛异常），反序列化也按 name 查找现有实例。 */
    enum EnumSingleton {
        INSTANCE;
        private final Map<String, String> config = new HashMap<>();
        Map<String, String> config() { return config; }
    }

    static void singletons() throws Exception {
        System.out.println("== 单例 ==");
        // 并发验证 DCL 只创建一个实例
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch gate = new CountDownLatch(1);
        java.util.Set<DclSingleton> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 64; i++) {
            pool.submit(() -> {
                try { gate.await(); } catch (InterruptedException ignored) { }
                seen.add(DclSingleton.getInstance());
            });
        }
        gate.countDown();
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("  DCL：64 个线程并发 getInstance，得到不同实例数 = " + seen.size());
        System.out.println("  静态内部类：" + (HolderSingleton.getInstance() == HolderSingleton.getInstance()) + "（首次调用 getInstance 才加载 Holder，天然懒加载）");

        // 反射破坏
        Constructor<FragileSingleton> c = FragileSingleton.class.getDeclaredConstructor();
        c.setAccessible(true);
        FragileSingleton another = c.newInstance();
        System.out.println("  反射破坏普通单例：another == INSTANCE ? " + (another == FragileSingleton.INSTANCE));

        // 反序列化破坏
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(FragileSingleton.INSTANCE);
        }
        Object deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            deserialized = ois.readObject();
        }
        System.out.println("  反序列化破坏普通单例：deserialized == INSTANCE ? " + (deserialized == FragileSingleton.INSTANCE) + "（加 readResolve 可防）");

        try {
            Constructor<?> ec = EnumSingleton.class.getDeclaredConstructor(String.class, int.class);
            ec.setAccessible(true);
            ec.newInstance("X", 1);
        } catch (Exception e) {
            System.out.println("  枚举单例反射创建 -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        EnumSingleton.INSTANCE.config().put("k", "v");
        System.out.println("  枚举单例是《Effective Java》推荐写法；Spring 的单例是容器级（每个容器一个），不是 JVM 级");
    }

    // ---------------- 工厂 ----------------
    sealed interface Notifier permits EmailNotifier, SmsNotifier, PushNotifier {
        String send(String msg);
    }
    record EmailNotifier() implements Notifier { public String send(String m) { return "Email: " + m; } }
    record SmsNotifier() implements Notifier { public String send(String m) { return "SMS: " + m; } }
    record PushNotifier() implements Notifier { public String send(String m) { return "Push: " + m; } }

    /** 简单工厂：一个静态方法 + switch。新增类型要改这里（违反开闭），但对小规模场景足够。 */
    static Notifier simpleFactory(String type) {
        return switch (type) {
            case "email" -> new EmailNotifier();
            case "sms" -> new SmsNotifier();
            case "push" -> new PushNotifier();
            default -> throw new IllegalArgumentException(type);
        };
    }

    /** 工厂方法：把“创建”延迟到子类/实现，注册表方式新增类型不改已有代码。 */
    static final Map<String, Supplier<Notifier>> REGISTRY = new HashMap<>();
    static {
        REGISTRY.put("email", EmailNotifier::new);
        REGISTRY.put("sms", SmsNotifier::new);
    }

    /** 抽象工厂：创建一“族”相关对象（同一主题的按钮 + 输入框），保证搭配一致。 */
    interface UiFactory {
        String button();
        String textField();
    }
    static class DarkUiFactory implements UiFactory {
        public String button() { return "[dark button]"; }
        public String textField() { return "[dark input]"; }
    }
    static class LightUiFactory implements UiFactory {
        public String button() { return "[light button]"; }
        public String textField() { return "[light input]"; }
    }

    static void factories() {
        System.out.println("\n== 工厂 ==");
        System.out.println("  简单工厂: " + simpleFactory("sms").send("hi"));
        REGISTRY.put("push", PushNotifier::new); // 运行时注册新类型，工厂代码不变
        System.out.println("  工厂方法(注册表): " + REGISTRY.get("push").get().send("hi") + "  已注册 " + REGISTRY.keySet());
        for (UiFactory f : new UiFactory[]{new DarkUiFactory(), new LightUiFactory()}) {
            System.out.println("  抽象工厂: " + f.button() + " " + f.textField());
        }
        System.out.println("  JDK 中：Calendar.getInstance() / NumberFormat.getInstance() 简单工厂；Collection.iterator() 工厂方法；");
        System.out.println("          DocumentBuilderFactory 抽象工厂；Spring BeanFactory / FactoryBean；SPI ServiceLoader 是“配置驱动的工厂”");
    }

    // ---------------- 建造者 ----------------
    record HttpRequest(String url, String method, Map<String, String> headers, String body, int timeoutMs) {
        static Builder builder(String url) { return new Builder(url); }

        static class Builder {
            private final String url;
            private String method = "GET";
            private final Map<String, String> headers = new HashMap<>();
            private String body;
            private int timeoutMs = 5000;

            Builder(String url) { this.url = url; }
            Builder method(String m) { this.method = m; return this; }
            Builder header(String k, String v) { headers.put(k, v); return this; }
            Builder body(String b) { this.body = b; return this; }
            Builder timeout(int ms) { this.timeoutMs = ms; return this; }

            HttpRequest build() {
                if (body != null && method.equals("GET")) {
                    throw new IllegalStateException("GET 请求不应有 body"); // 在 build 时集中校验
                }
                return new HttpRequest(url, method, Map.copyOf(headers), body, timeoutMs);
            }
        }
    }

    static void builder() {
        System.out.println("\n== 建造者 ==");
        HttpRequest req = HttpRequest.builder("https://api.example.com/orders")
                .method("POST")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer xxx")
                .body("{\"sku\":\"A1\"}")
                .timeout(3000)
                .build();
        System.out.println("  " + req);
        try {
            HttpRequest.builder("https://x").body("oops").build();
        } catch (IllegalStateException e) {
            System.out.println("  build 时校验: " + e.getMessage());
        }
        System.out.println("  解决的问题：构造器参数过多（伸缩构造器反模式）、可选参数、不可变对象、构建过程校验");
        System.out.println("  实际写法：Lombok @Builder 一行生成；JDK 里 StringBuilder、Stream.Builder、HttpRequest.newBuilder()（java.net.http）");
    }

    // ---------------- 原型 ----------------
    static class Document implements Cloneable {
        String title;
        java.util.List<String> tags = new java.util.ArrayList<>();

        @Override
        public Document clone() {
            try {
                Document d = (Document) super.clone(); // 浅拷贝：tags 引用共享
                d.tags = new java.util.ArrayList<>(tags); // 手动深拷贝可变字段
                return d;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    static void prototype() {
        System.out.println("\n== 原型（clone） ==");
        Document a = new Document();
        a.title = "spec";
        a.tags.add("v1");
        Document b = a.clone();
        b.tags.add("v2");
        System.out.println("  a.tags=" + a.tags + " b.tags=" + b.tags + "（深拷贝后互不影响；若只 super.clone() 则共享同一个 List）");
        System.out.println("  现代替代：拷贝构造器 / record 的 with 方法 / 序列化深拷贝；Cloneable 设计被 Effective Java 批评为“破碎的接口”");
    }

    static void printSummary() {
        System.out.println("""

                选型速查：
                  只要一个实例且全局共享（配置、连接池、线程池）-> 单例（优先枚举或静态内部类；Spring 环境交给容器管理）
                  根据参数决定创建哪个实现 -> 简单工厂；需要扩展开放 -> 工厂方法 + 注册表 / SPI
                  一组对象必须配套 -> 抽象工厂
                  参数多、可选多、需要不可变 -> 建造者
                  创建成本高且对象相似 -> 原型
                创建型模式的共同目标：把“如何创建”从“如何使用”中分离，让使用方只依赖抽象
                参考：iluwatar/java-design-patterns、《Effective Java》第 2 章、《Head First 设计模式》、refactoringguru.cn/design-patterns
                """);
    }
}
