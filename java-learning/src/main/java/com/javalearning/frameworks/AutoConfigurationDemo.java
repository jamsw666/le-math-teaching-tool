package com.javalearning.frameworks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 知识点 7-3：Spring Boot 自动装配与 Starter。
 *
 * <p>模拟 @EnableAutoConfiguration 的核心：从"候选列表"（对应 META-INF/spring/…AutoConfiguration.imports）读取自动配置类，
 * 逐个评估 @ConditionalOnClass / @ConditionalOnMissingBean / @ConditionalOnProperty 条件，
 * 满足则注册默认 Bean；用户自定义 Bean 优先（约定优于配置）。并演示 @ConfigurationProperties 的属性绑定。
 */
public class AutoConfigurationDemo {

    /** 一条自动配置 = 名称 + 条件 + 创建 Bean 的工厂。 */
    record AutoConfiguration(String name, String beanName, List<Predicate<Context>> conditions, Function<Context, Object> factory) { }

    /** 容器上下文：类路径、已注册 Bean、配置属性。 */
    static class Context {
        final java.util.Set<String> classpath = new java.util.HashSet<>();
        final Map<String, Object> beans = new LinkedHashMap<>();
        final Properties properties = new Properties();

        boolean hasClass(String name) { return classpath.contains(name); }
        boolean hasBean(String name) { return beans.containsKey(name); }
        String property(String key, String def) { return properties.getProperty(key, def); }
    }

    // ---------- 条件注解的函数式表达 ----------
    static Predicate<Context> conditionalOnClass(String cls) { return ctx -> ctx.hasClass(cls); }
    static Predicate<Context> conditionalOnMissingBean(String bean) { return ctx -> !ctx.hasBean(bean); }
    static Predicate<Context> conditionalOnProperty(String key, String expected) { return ctx -> expected.equals(ctx.property(key, "")); }

    /** 对应 @ConfigurationProperties(prefix = "server") 的类型安全绑定。 */
    record ServerProperties(int port, String contextPath) {
        static ServerProperties bind(Properties p) {
            return new ServerProperties(Integer.parseInt(p.getProperty("server.port", "8080")), p.getProperty("server.servlet.context-path", "/"));
        }
    }

    /** 模拟 spring-boot-autoconfigure 中的候选列表。 */
    static final List<AutoConfiguration> CANDIDATES = List.of(
            new AutoConfiguration("DataSourceAutoConfiguration", "dataSource",
                    List.of(conditionalOnClass("javax.sql.DataSource"), conditionalOnMissingBean("dataSource")),
                    ctx -> "HikariDataSource(url=" + ctx.property("spring.datasource.url", "jdbc:h2:mem:default") + ")"),
            new AutoConfiguration("RedisAutoConfiguration", "redisTemplate",
                    List.of(conditionalOnClass("io.lettuce.core.RedisClient"), conditionalOnMissingBean("redisTemplate")),
                    ctx -> "RedisTemplate(host=" + ctx.property("spring.data.redis.host", "localhost") + ")"),
            new AutoConfiguration("WebMvcAutoConfiguration", "dispatcherServlet",
                    List.of(conditionalOnClass("jakarta.servlet.Servlet"), conditionalOnMissingBean("dispatcherServlet")),
                    ctx -> "DispatcherServlet on port " + ServerProperties.bind(ctx.properties).port()),
            new AutoConfiguration("ActuatorAutoConfiguration", "healthEndpoint",
                    List.of(conditionalOnProperty("management.endpoint.health.enabled", "true")),
                    ctx -> "HealthEndpoint"));

    static void run(String title, Context ctx) {
        System.out.println("== " + title + " ==");
        System.out.println("  类路径: " + ctx.classpath + "，用户 Bean: " + ctx.beans.keySet());
        for (AutoConfiguration ac : CANDIDATES) {
            boolean matched = ac.conditions().stream().allMatch(c -> c.test(ctx));
            if (matched) {
                ctx.beans.put(ac.beanName(), ac.factory().apply(ctx));
                System.out.println("  ✔ " + ac.name() + " 生效 -> 注册 " + ac.beanName());
            } else {
                System.out.println("  ✘ " + ac.name() + " 跳过（条件不满足）");
            }
        }
        System.out.println("  最终 Bean: " + ctx.beans + "\n");
    }

    public static void main(String[] args) {
        Context web = new Context();
        web.classpath.addAll(List.of("javax.sql.DataSource", "jakarta.servlet.Servlet")); // 引入了 starter-web 与 starter-jdbc
        web.properties.setProperty("server.port", "9090");
        web.properties.setProperty("management.endpoint.health.enabled", "true");
        run("只引入 starter-web + starter-jdbc", web);

        Context custom = new Context();
        custom.classpath.addAll(List.of("javax.sql.DataSource", "io.lettuce.core.RedisClient"));
        custom.beans.put("dataSource", "用户自定义的 DruidDataSource");   // 用户声明了同名 Bean
        run("用户自定义 dataSource，且引入 starter-data-redis", custom);

        System.out.println("""
                真实机制：
                  @SpringBootApplication = @Configuration + @ComponentScan + @EnableAutoConfiguration
                  @EnableAutoConfiguration 通过 @Import(AutoConfigurationImportSelector) 读取
                    META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports（Boot 2.7+，之前是 spring.factories）
                  每个自动配置类用 @ConditionalOnClass / OnMissingBean / OnProperty / OnWebApplication 等条件决定是否生效
                  @ConditionalOnMissingBean 使用户 Bean 优先 -> "约定优于配置，配置可覆盖"
                  自动配置类的加载顺序由 @AutoConfigureBefore/After 控制，并且在用户配置类之后处理（这样才能判断 MissingBean）
                Starter 的本质：
                  xxx-spring-boot-starter 只是一个 pom（依赖聚合）+ 一个 xxx-spring-boot-autoconfigure（配置类 + 条件 + Properties）
                  自定义 Starter 三步：写 @ConfigurationProperties、写 @AutoConfiguration 类、在 imports 文件登记
                调试：--debug 启动参数打印 CONDITIONS EVALUATION REPORT（哪些匹配/未匹配及原因）；actuator 的 /conditions 端点同理
                配置优先级（高到低）：命令行参数 > 环境变量 > application-{profile}.yml > application.yml > @PropertySource > 默认值
                """);
    }
}
