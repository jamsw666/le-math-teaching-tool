package com.javalearning.patterns;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 10-2 结构型模式：代理 / 适配器 / 装饰器（+ 外观、组合、享元）。
 *
 * <p>代理：静态代理 -> JDK 动态代理（可叠加多个横切逻辑），对应 Spring AOP；
 * 适配器：让不兼容接口协作（对象适配器），对应 InputStreamReader、Arrays.asList；
 * 装饰器：运行时动态叠加职责，对应 java.io 流体系；三者结构相似但意图不同，示例末尾做对比。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.patterns.StructuralPatternsDemo
 */
public class StructuralPatternsDemo {

    public static void main(String[] args) {
        proxy();
        adapter();
        decorator();
        facade();
        composite();
        flyweight();
        printComparison();
    }

    // ---------------- 代理 ----------------
    interface UserService {
        String findName(long id);
        void delete(long id);
    }

    static class UserServiceImpl implements UserService {
        public String findName(long id) { return "user-" + id; }
        public void delete(long id) { System.out.println("      [真实对象] 删除用户 " + id); }
    }

    /** 静态代理：手写一个同接口的类包一层。每个接口都要写一遍，方法多了很啰嗦。 */
    static class LoggingUserServiceProxy implements UserService {
        private final UserService target;
        LoggingUserServiceProxy(UserService target) { this.target = target; }
        public String findName(long id) {
            System.out.println("      [静态代理] 调用 findName(" + id + ")");
            return target.findName(id);
        }
        public void delete(long id) {
            System.out.println("      [静态代理] 调用 delete(" + id + ")");
            target.delete(id);
        }
    }

    /** 动态代理：一个 InvocationHandler 适用于任何接口。 */
    static <T> T withTiming(T target, Class<T> iface) {
        InvocationHandler h = (proxy, method, args) -> {
            long t0 = System.nanoTime();
            try {
                return method.invoke(target, args);
            } finally {
                System.out.printf("      [计时代理] %s 耗时 %d μs%n", method.getName(), (System.nanoTime() - t0) / 1000);
            }
        };
        return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, h));
    }

    static <T> T withAuth(T target, Class<T> iface, String role) {
        InvocationHandler h = (proxy, method, args) -> {
            if (method.getName().startsWith("delete") && !role.equals("admin")) {
                throw new SecurityException("角色 " + role + " 无权执行 " + method.getName());
            }
            return method.invoke(target, args);
        };
        return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, h));
    }

    static void proxy() {
        System.out.println("== 代理 ==");
        UserService real = new UserServiceImpl();
        System.out.println("  静态代理:");
        new LoggingUserServiceProxy(real).findName(1);

        System.out.println("  动态代理叠加（权限 -> 计时 -> 真实对象）:");
        UserService proxied = withAuth(withTiming(real, UserService.class), UserService.class, "guest");
        System.out.println("      findName -> " + proxied.findName(2));
        try {
            proxied.delete(2);
        } catch (SecurityException e) {
            System.out.println("      delete -> " + e.getMessage());
        }
        System.out.println("  代理类名: " + proxied.getClass().getSimpleName() + "（运行时生成字节码）");
        System.out.println("  JDK 代理要求有接口；CGLIB / ByteBuddy 生成子类可代理类（final 方法除外）。Spring AOP、MyBatis Mapper、RPC 客户端桩、Mockito 都基于此");
    }

    // ---------------- 适配器 ----------------
    /** 系统期望的接口。 */
    interface Logger {
        void log(String level, String msg);
    }

    /** 第三方库，接口不兼容。 */
    static class LegacyLogSdk {
        void writeLine(int severity, String text) {
            System.out.println("      [LegacySdk severity=" + severity + "] " + text);
        }
    }

    /** 对象适配器：持有被适配者，做协议转换。 */
    static class LegacyLogAdapter implements Logger {
        private final LegacyLogSdk sdk;
        private static final Map<String, Integer> LEVELS = Map.of("INFO", 1, "WARN", 2, "ERROR", 3);
        LegacyLogAdapter(LegacyLogSdk sdk) { this.sdk = sdk; }
        public void log(String level, String msg) {
            sdk.writeLine(LEVELS.getOrDefault(level, 0), "[" + level + "] " + msg);
        }
    }

    static void adapter() {
        System.out.println("\n== 适配器 ==");
        Logger logger = new LegacyLogAdapter(new LegacyLogSdk());
        logger.log("WARN", "磁盘空间不足");
        System.out.println("  JDK 中：InputStreamReader（字节流 -> 字符流）、Arrays.asList（数组 -> List）、Collections.enumeration；");
        System.out.println("  Spring HandlerAdapter 让 DispatcherServlet 统一调用各种 Controller；SLF4J 的各种 binding 本质也是适配器");
    }

    // ---------------- 装饰器 ----------------
    interface DataSource {
        void write(String data);
        String read();
    }

    static class MemorySource implements DataSource {
        private String stored = "";
        public void write(String data) { stored = data; System.out.println("      [存储] 写入 " + data); }
        public String read() { return stored; }
    }

    /** 装饰器基类：实现同一接口并持有同类型组件 —— 与代理结构一样，但意图是“加功能”且由调用方自由组合。 */
    static abstract class SourceDecorator implements DataSource {
        protected final DataSource inner;
        SourceDecorator(DataSource inner) { this.inner = inner; }
        public void write(String data) { inner.write(data); }
        public String read() { return inner.read(); }
    }

    static class CompressDecorator extends SourceDecorator {
        CompressDecorator(DataSource in) { super(in); }
        public void write(String d) { super.write("zip(" + d + ")"); }
        public String read() { String r = super.read(); return r.substring(4, r.length() - 1); }
    }

    static class EncryptDecorator extends SourceDecorator {
        EncryptDecorator(DataSource in) { super(in); }
        public void write(String d) { super.write(rot13(d)); }
        public String read() { return rot13(super.read()); }
        static String rot13(String s) {
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (c >= 'a' && c <= 'z') sb.append((char) ('a' + (c - 'a' + 13) % 26));
                else if (c >= 'A' && c <= 'Z') sb.append((char) ('A' + (c - 'A' + 13) % 26));
                else sb.append(c);
            }
            return sb.toString();
        }
    }

    static void decorator() {
        System.out.println("\n== 装饰器 ==");
        DataSource ds = new EncryptDecorator(new CompressDecorator(new MemorySource()));
        ds.write("hello");
        System.out.println("      读出: " + ds.read() + "（加密 -> 压缩 -> 存储，读取时逆序还原）");
        System.out.println("  与 new BufferedReader(new InputStreamReader(new FileInputStream(f))) 同构：java.io 是装饰器的教科书案例");
        System.out.println("  Collections.unmodifiableList / synchronizedList 也是装饰器；Servlet 的 HttpServletRequestWrapper 同理");
    }

    // ---------------- 外观 ----------------
    static class OrderFacade {
        String placeOrder(String sku) {
            // 内部协调多个子系统，调用方只需一个方法
            String steps = "库存.lock(" + sku + ") -> 支付.charge() -> 物流.create() -> 通知.send()";
            return "下单成功: " + steps;
        }
    }

    static void facade() {
        System.out.println("\n== 外观 ==");
        System.out.println("  " + new OrderFacade().placeOrder("A1"));
        System.out.println("  为复杂子系统提供统一入口，降低耦合：SLF4J LoggerFactory、Spring JdbcTemplate（封装连接/语句/异常/关闭）、应用层 Service 常常就是外观");
    }

    // ---------------- 组合 ----------------
    sealed interface FsNode permits FileNode, DirNode {
        long size();
        String name();
    }
    record FileNode(String name, long size) implements FsNode { }
    record DirNode(String name, List<FsNode> children) implements FsNode {
        public long size() { return children.stream().mapToLong(FsNode::size).sum(); } // 递归：对容器和叶子一视同仁
    }

    static void composite() {
        System.out.println("\n== 组合 ==");
        FsNode root = new DirNode("/", List.of(
                new FileNode("a.txt", 100),
                new DirNode("src", List.of(new FileNode("Main.java", 2000), new DirNode("util", List.of(new FileNode("U.java", 300))))),
                new FileNode("b.log", 50)));
        System.out.println("  目录树总大小 = " + root.size() + "（调用方不关心是文件还是目录）");
        System.out.println("  JDK/框架中：Swing 容器、Spring CompositeCacheManager、菜单树、组织架构、表达式树（见 PatternMatchingDemo）");
    }

    // ---------------- 享元 ----------------
    record Glyph(char ch, String font) { } // 内在状态：可共享
    static class GlyphFactory {
        private final Map<String, Glyph> pool = new HashMap<>();
        int created = 0;
        Glyph get(char ch, String font) {
            return pool.computeIfAbsent(ch + font, k -> { created++; return new Glyph(ch, font); });
        }
    }

    static void flyweight() {
        System.out.println("\n== 享元 ==");
        GlyphFactory f = new GlyphFactory();
        String text = "hello world hello java";
        List<Glyph> rendered = new ArrayList<>();
        for (char c : text.toCharArray()) rendered.add(f.get(c, "Mono")); // 外在状态（位置）由调用方持有
        System.out.println("  渲染 " + text.length() + " 个字符，只创建了 " + f.created + " 个 Glyph 对象");
        System.out.println("  JDK 中：Integer.valueOf 缓存 -128~127、String 常量池、Boolean.TRUE/FALSE；线程池/连接池是“池化”思想的近亲");
    }

    static void printComparison() {
        System.out.println("""

                代理 vs 装饰器 vs 适配器（结构都是“实现同一接口 + 持有一个对象”，区别在意图）：
                  代理     控制访问：权限、懒加载、远程调用、缓存、事务；调用方通常不知道有代理，代理与目标接口相同
                  装饰器   增强功能：由调用方显式层层包装、可任意组合顺序；接口相同
                  适配器   转换接口：让不兼容的接口协作；接口不同（这是与前两者的根本区别）
                  外观     简化接口：为一组子系统提供更高层的统一入口；接口不同且更粗粒度
                  桥接     分离抽象与实现两个维度独立变化（JDBC Driver 与 DriverManager；Slf4j 门面与实现也常被归为桥接）
                  组合     树形结构，容器与叶子统一处理
                  享元     共享内在状态减少对象数
                参考：iluwatar/java-design-patterns、Spring AOP 源码（JdkDynamicAopProxy / CglibAopProxy）、java.io 包
                """);
    }
}
