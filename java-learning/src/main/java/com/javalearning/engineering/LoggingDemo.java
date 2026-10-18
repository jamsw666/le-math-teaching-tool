package com.javalearning.engineering;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 9-4 日志：SLF4J / Logback / Log4j2。
 *
 * <p>真实使用 SLF4J API（本工程绑定 slf4j-simple，配置见 src/main/resources/simplelogger.properties）：
 * 占位符参数化、级别控制、异常堆栈、MDC 链路追踪 ID、以及日志对性能的影响测量。
 * Logback 生产配置样例见 docs/samples/logback-spring.xml。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.engineering.LoggingDemo
 */
public class LoggingDemo {

    private static final Logger log = LoggerFactory.getLogger(LoggingDemo.class);

    public static void main(String[] args) throws Exception {
        levelsAndPlaceholders();
        exceptionLogging();
        mdcTracing();
        performance();
        printSummary();
    }

    static void levelsAndPlaceholders() {
        System.out.println("== 级别与占位符（当前配置默认级别 INFO，所以 TRACE/DEBUG 不会输出） ==");
        log.trace("trace: 最细粒度，通常只在排查特定问题时开");
        log.debug("debug: 开发期信息，生产默认关闭");
        log.info("info: 关键业务节点，用户 {} 下单 {} 件", "alice", 3);
        log.warn("warn: 可恢复的异常情况，比如重试第 {} 次", 2);
        log.error("error: 需要人工关注的失败");
        System.out.println("  级别顺序 TRACE < DEBUG < INFO < WARN < ERROR；配置为 INFO 时输出 INFO 及以上");
        System.out.println("  占位符 {} 延迟拼接：级别未开启时不会执行 toString，避免 \"debug(\\\"x=\\\" + obj)\" 式的无谓开销");
        System.out.println("  isDebugEnabled() 只在参数本身构造昂贵（如 JSON 序列化）时才需要显式判断");
    }

    static void exceptionLogging() {
        System.out.println("\n== 异常日志：异常对象放最后一个参数，才会打印堆栈 ==");
        try {
            Integer.parseInt("not-a-number");
        } catch (NumberFormatException e) {
            log.error("解析失败 input={}", "not-a-number", e); // 正确：堆栈完整
            log.error("解析失败（错误写法，只有 message）: {}", e.getMessage()); // 常见错误：丢掉了堆栈
        }
    }

    static void mdcTracing() throws InterruptedException {
        System.out.println("\n== MDC：把 traceId 绑定到当前线程，所有日志自动携带 ==");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 3; i++) {
            String traceId = UUID.randomUUID().toString().substring(0, 8);
            pool.submit(() -> {
                MDC.put("traceId", traceId); // 真实项目在 Filter / 拦截器里从请求头取或生成
                try {
                    // slf4j-simple 不渲染 MDC，这里手动取出显示；Logback / Log4j2 用 pattern 里的 %X{traceId} 自动输出
                    log.info("[traceId={}] 开始处理请求", MDC.get("traceId"));
                    log.info("[traceId={}] 调用下游服务", MDC.get("traceId"));
                } finally {
                    MDC.clear(); // 线程池线程复用，必须清理，否则串 traceId
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  MDC 基于 ThreadLocal；跨线程（线程池、CompletableFuture）需要手动传递或用 TransmittableThreadLocal / Micrometer 的 context-propagation");
        System.out.println("  Logback pattern 用 %X{traceId} 输出；ELK / Loki 按 traceId 聚合一次请求的所有日志");
    }

    static void performance() {
        System.out.println("\n== 性能：字符串拼接 vs 占位符（DEBUG 级别关闭时，10 万次） ==");
        Object heavy = new Object() {
            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 50; i++) sb.append(i);
                return sb.toString();
            }
        };
        long t0 = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            log.debug("value=" + heavy); // toString 每次都执行
        }
        long concat = System.nanoTime() - t0;
        t0 = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            log.debug("value={}", heavy); // 级别关闭直接返回
        }
        long placeholder = System.nanoTime() - t0;
        System.out.printf("  拼接 %.1f ms  vs  占位符 %.1f ms%n", concat / 1e6, placeholder / 1e6);
        System.out.println("  生产环境再加一层：Logback AsyncAppender / Log4j2 全异步（Disruptor 无锁队列）让业务线程不等磁盘 IO");
    }

    static void printSummary() {
        System.out.println("""

                门面与实现：
                  SLF4J 是门面（API），Logback / Log4j2 / java.util.logging / slf4j-simple 是实现；代码只依赖 SLF4J，换实现只改依赖
                  桥接：jcl-over-slf4j、log4j-over-slf4j、jul-to-slf4j 把其他框架的日志统一收编到 SLF4J；注意排除重复绑定（classpath 多个 StaticLoggerBinder 警告）
                  Spring Boot 默认 Logback（spring-boot-starter-logging）；换 Log4j2 需排除默认 starter 再引入 starter-log4j2
                Logback 核心概念：Logger（层级继承，root 兜底）/ Appender（Console、RollingFile、Async、Kafka）/ Encoder & Pattern / Filter（ThresholdFilter、LevelFilter）
                  RollingPolicy：SizeAndTimeBasedRollingPolicy 按天 + 按大小滚动、maxHistory 保留天数、totalSizeCap 总上限
                  logback-spring.xml 支持 <springProfile name="prod"> 按环境区分配置
                Log4j2 优势：异步日志吞吐高、垃圾少（GC-free）、配置热更新；2021 年 Log4Shell 漏洞（JNDI lookup）提醒要及时升级（>= 2.17）
                规范：
                  - 不打敏感信息（密码、身份证、完整手机号），需要时脱敏
                  - 不在循环里打 INFO；大对象不要直接 toString
                  - 每条日志要能回答“谁、做了什么、结果如何、关键参数”，便于检索
                  - 统一 JSON 格式（logstash-logback-encoder）方便采集；traceId / spanId 必带
                  - 异常只在最终处理处打一次，避免层层 catch 重复打印
                参考：qos-ch/slf4j、qos-ch/logback、apache/logging-log4j2、logfellow/logstash-logback-encoder、alibaba/transmittable-thread-local
                """);
    }
}
