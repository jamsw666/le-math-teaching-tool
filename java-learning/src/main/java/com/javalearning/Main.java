package com.javalearning;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 示例运行器：列出知识图谱全部示例，或按名称 / 章节运行。
 *
 * <pre>
 *   mvn -q exec:java                                   # 等价于 --list
 *   mvn -q exec:java -Dexec.args="OopDemo"             # 按简单类名运行
 *   mvn -q exec:java -Dexec.args="03"                  # 运行第 3 章全部示例
 *   mvn -q exec:java -Ddemo=com.javalearning.basics.OopDemo   # 直接指定主类（不经过 Main）
 * </pre>
 */
public final class Main {

    record Chapter(String id, String title, String doc, String pkg, List<String> demos) { }

    static final List<Chapter> CHAPTERS = List.of(
            new Chapter("01", "Java 基础", "docs/01-java-basics.md", "basics", List.of(
                    "SyntaxAndTypesDemo", "OperatorsAndControlFlowDemo", "OopDemo", "InterfaceVsAbstractDemo", "ExceptionDemo",
                    "GenericsDemo", "AnnotationReflectionDemo", "EnumRecordInnerClassDemo", "StringPoolDemo")),
            new Chapter("02", "集合框架", "docs/02-collections.md", "collections", List.of(
                    "ListDemo", "SetDemo", "MapDemo", "QueueDemo", "IteratorAndCollectionsDemo", "StreamDemo", "FailFastAndResizeDemo")),
            new Chapter("03", "并发编程", "docs/03-concurrency.md", "concurrency", List.of(
                    "ThreadPoolDemo", "JmmDemo", "SynchronizedVolatileDemo", "LockDemo", "AqsDemo", "AtomicCasDemo",
                    "ConcurrentCollectionsDemo", "CompletableFutureDemo", "ThreadLocalDemo")),
            new Chapter("04", "JVM", "docs/04-jvm.md", "jvm", List.of(
                    "ClassLoadingDemo", "RuntimeDataAreaDemo", "ObjectLayoutDemo", "GcDemo", "JitDemo", "BytecodeDemo",
                    "TuningToolsDemo", "OomDemo")),
            new Chapter("05", "IO 与网络", "docs/05-io-network.md", "io", List.of(
                    "FileStreamDemo", "BufferedDecoratorDemo", "SerializationDemo", "NioDemo", "SocketDemo", "ReactorNettyDemo", "ZeroCopyDemo")),
            new Chapter("06", "Java 新特性", "docs/06-modern-java.md", "modern", List.of(
                    "LambdaDemo", "OptionalDateTimeDemo", "ModuleSystemDemo", "VarDemo", "SwitchTextBlockDemo", "RecordSealedDemo",
                    "PatternMatchingDemo", "VirtualThreadDemo")),
            new Chapter("07", "主流框架", "docs/07-frameworks.md", "frameworks", List.of(
                    "MiniIocAopDemo", "MiniMvcDemo", "AutoConfigurationDemo", "CloudComponentsDemo", "MiniMyBatisDemo",
                    "JpaConceptsDemo", "SecurityFilterChainDemo")),
            new Chapter("08", "数据库与中间件", "docs/08-database-middleware.md", "middleware", List.of(
                    "JdbcConnectionPoolDemo", "MysqlIndexTxDemo", "RedisDemo", "MessageQueueDemo", "ElasticsearchDemo",
                    "ShardingDemo", "CacheConsistencyDemo")),
            new Chapter("09", "工程化与工具", "docs/09-engineering.md", "engineering", List.of(
                    "BuildToolsDemo", "GitWorkflowDemo", "UnitTestDemo", "LoggingDemo", "DockerK8sDemo", "CicdDemo", "CodeStyleDemo")),
            new Chapter("10", "设计模式与架构", "docs/10-patterns-architecture.md", "patterns", List.of(
                    "CreationalPatternsDemo", "StructuralPatternsDemo", "BehavioralPatternsDemo", "SolidDemo", "DddDemo",
                    "DistributedDemo", "ResilienceDemo", "SystemDesignDemo")));

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("--list") || args[0].equals("-l")) {
            list();
            return;
        }
        Map<String, String> index = index();
        List<String> toRun = new ArrayList<>();
        for (String arg : args) {
            String key = arg.trim();
            if (key.matches("\\d{1,2}")) {
                String id = String.format(Locale.ROOT, "%02d", Integer.parseInt(key));
                CHAPTERS.stream().filter(c -> c.id().equals(id)).findFirst()
                        .ifPresentOrElse(c -> c.demos().forEach(d -> toRun.add(className(c, d))),
                                () -> System.err.println("没有第 " + key + " 章"));
            } else if (key.contains(".")) {
                toRun.add(key);
            } else {
                String fqcn = index.get(key.toLowerCase(Locale.ROOT));
                if (fqcn == null) {
                    System.err.println("未知示例: " + key + "（用 --list 查看全部）");
                } else {
                    toRun.add(fqcn);
                }
            }
        }
        for (String fqcn : toRun) {
            run(fqcn);
        }
    }

    static void list() {
        System.out.println("Java 知识图谱学习工程 — 共 " + CHAPTERS.stream().mapToInt(c -> c.demos().size()).sum() + " 个示例\n");
        for (Chapter c : CHAPTERS) {
            System.out.printf("%s %s  (%s)%n", c.id(), c.title(), c.doc());
            for (String d : c.demos()) {
                System.out.printf("    %-30s %s%n", d, className(c, d));
            }
        }
        System.out.println("""

                运行方式：
                  mvn -q exec:java -Dexec.args="OopDemo"             按类名运行一个示例
                  mvn -q exec:java -Dexec.args="03"                  运行某一章全部示例
                  mvn -q exec:java -Ddemo=<完整类名>                  直接指定主类
                提示：FailFastAndResizeDemo 需要 JVM 参数 --add-opens java.base/java.util=ALL-UNNAMED 才能反射读取 ArrayList 内部数组，
                     否则会自动降级为模拟；TuningToolsDemo 末尾会停留 10 秒供你用 jps/jstack 观察。""");
    }

    static Map<String, String> index() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Chapter c : CHAPTERS) {
            for (String d : c.demos()) {
                m.put(d.toLowerCase(Locale.ROOT), className(c, d));
            }
        }
        return m;
    }

    static String className(Chapter c, String demo) {
        return "com.javalearning." + c.pkg() + "." + demo;
    }

    static void run(String fqcn) throws Exception {
        String bar = "=".repeat(Math.max(10, 78 - fqcn.length()));
        System.out.println("\n" + bar + " " + fqcn + "\n");
        long t0 = System.currentTimeMillis();
        try {
            Method main = Class.forName(fqcn).getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
        } catch (ClassNotFoundException e) {
            System.err.println("找不到类 " + fqcn);
        } catch (java.lang.reflect.InvocationTargetException e) {
            System.err.println("示例抛出异常: " + e.getCause());
        }
        System.out.printf("%n[%s 结束，耗时 %d ms]%n", fqcn.substring(fqcn.lastIndexOf('.') + 1), System.currentTimeMillis() - t0);
    }
}
