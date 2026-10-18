package com.javalearning.frameworks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识点 7-1：Spring 核心 —— IoC / DI / AOP / 事务。
 *
 * <p>用约 120 行实现一个"迷你 Spring 容器"：扫描 @Component → 实例化 → @Autowired 字段注入 →
 * BeanPostProcessor 为带 @Transactional 的 Bean 创建 JDK 动态代理 → 代理中织入事务切面。
 * 看懂它，就看懂了 Spring 的骨架：BeanDefinition、三级缓存、后置处理器、代理对象替换原始 Bean。
 */
public class MiniIocAopDemo {

    // ---------- 注解 ----------
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) @interface Component { }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) @interface Autowired { }
    @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.METHOD, ElementType.TYPE}) @interface Transactional { }

    /** Bean 后置处理器：Spring AOP 就是通过它在初始化后用代理替换原对象。 */
    interface BeanPostProcessor {
        Object postProcessAfterInitialization(Object bean, String name);
    }

    // ---------- 迷你容器 ----------
    static class MiniApplicationContext {
        private final Map<String, Object> singletonObjects = new LinkedHashMap<>();  // 一级缓存：完整 Bean
        private final Map<String, Object> earlySingletonObjects = new LinkedHashMap<>(); // 二级缓存：提前暴露的半成品（解决循环依赖）
        private final List<BeanPostProcessor> postProcessors = new ArrayList<>();
        private final Map<String, Class<?>> beanDefinitions = new LinkedHashMap<>();

        MiniApplicationContext(Class<?>... candidates) {
            // 1. 扫描：真实 Spring 扫描类路径，这里直接传入候选类
            for (Class<?> c : candidates) {
                if (c.isAnnotationPresent(Component.class)) {
                    beanDefinitions.put(decapitalize(c.getSimpleName()), c);
                }
            }
            postProcessors.add(new TransactionalProxyPostProcessor());
            // 2. 预实例化所有单例
            beanDefinitions.keySet().forEach(this::getBean);
        }

        @SuppressWarnings("unchecked")
        <T> T getBean(String name) {
            if (singletonObjects.containsKey(name)) return (T) singletonObjects.get(name);
            if (earlySingletonObjects.containsKey(name)) return (T) earlySingletonObjects.get(name); // 循环依赖时拿半成品
            try {
                Class<?> type = beanDefinitions.get(name);
                if (type == null) throw new IllegalStateException("No bean named " + name);
                Object instance = type.getDeclaredConstructor().newInstance();   // 实例化（反射调用构造器）
                earlySingletonObjects.put(name, instance);                       // 提前暴露
                populate(instance);                                              // 属性填充（依赖注入）
                Object bean = instance;
                for (BeanPostProcessor p : postProcessors) {                     // 初始化后处理：可能返回代理
                    bean = p.postProcessAfterInitialization(bean, name);
                }
                earlySingletonObjects.remove(name);
                singletonObjects.put(name, bean);
                System.out.println("  [容器] 创建 Bean '" + name + "' -> " + (Proxy.isProxyClass(bean.getClass()) ? "代理对象" : "原始对象"));
                return (T) bean;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        private void populate(Object instance) throws IllegalAccessException {
            for (Field f : instance.getClass().getDeclaredFields()) {
                if (f.isAnnotationPresent(Autowired.class)) {
                    f.setAccessible(true);
                    // 按类型查找：找到 beanDefinitions 中可赋值给字段类型的 Bean
                    String dep = beanDefinitions.entrySet().stream()
                            .filter(e -> f.getType().isAssignableFrom(e.getValue()))
                            .map(Map.Entry::getKey).findFirst()
                            .orElseThrow(() -> new IllegalStateException("无法注入 " + f.getName()));
                    f.set(instance, getBean(dep));
                }
            }
        }

        static String decapitalize(String s) {
            return Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
    }

    /** 事务切面：方法前 begin，正常 commit，异常 rollback。 */
    static class TransactionalProxyPostProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(Object bean, String name) {
            boolean needsProxy = bean.getClass().isAnnotationPresent(Transactional.class)
                    || java.util.Arrays.stream(bean.getClass().getMethods()).anyMatch(m -> m.isAnnotationPresent(Transactional.class));
            if (!needsProxy || bean.getClass().getInterfaces().length == 0) return bean;
            return Proxy.newProxyInstance(bean.getClass().getClassLoader(), bean.getClass().getInterfaces(), (proxy, method, args) -> {
                var target = bean.getClass().getMethod(method.getName(), method.getParameterTypes());
                if (!target.isAnnotationPresent(Transactional.class)) return method.invoke(bean, args);
                System.out.println("    [事务] BEGIN " + method.getName());
                try {
                    Object result = method.invoke(bean, args);
                    System.out.println("    [事务] COMMIT");
                    return result;
                } catch (java.lang.reflect.InvocationTargetException e) {
                    System.out.println("    [事务] ROLLBACK 因为 " + e.getCause().getMessage());
                    throw e.getCause();
                }
            });
        }
    }

    // ---------- 业务代码：和写 Spring 应用一模一样 ----------
    interface OrderRepository { void save(String order); }
    interface OrderService { void place(String order); void placeAndFail(String order); }

    @Component
    static class JdbcOrderRepository implements OrderRepository {
        @Override
        public void save(String order) {
            System.out.println("    [仓储] INSERT INTO orders ... " + order);
        }
    }

    @Component
    static class DefaultOrderService implements OrderService {
        @Autowired
        private OrderRepository repository;   // 字段注入（真实项目推荐构造器注入）

        @Override
        @Transactional
        public void place(String order) {
            repository.save(order);
        }

        @Override
        @Transactional
        public void placeAndFail(String order) {
            repository.save(order);
            throw new IllegalStateException("库存不足");
        }
    }

    public static void main(String[] args) {
        System.out.println("== 启动迷你容器 ==");
        MiniApplicationContext ctx = new MiniApplicationContext(JdbcOrderRepository.class, DefaultOrderService.class);

        System.out.println("\n== 使用 Bean ==");
        OrderService service = ctx.getBean("defaultOrderService");
        service.place("订单#1");
        try {
            service.placeAndFail("订单#2");
        } catch (IllegalStateException e) {
            System.out.println("  调用方收到异常: " + e.getMessage());
        }

        System.out.println("""

                对照真实 Spring：
                  BeanDefinition          <- beanDefinitions（类元数据、作用域、是否懒加载）
                  DefaultListableBeanFactory.getBean / doCreateBean <- getBean（实例化 -> populateBean -> initializeBean）
                  三级缓存 singletonObjects / earlySingletonObjects / singletonFactories 解决字段注入的循环依赖（构造器注入无法解决）
                  BeanPostProcessor       <- AOP 入口：AbstractAutoProxyCreator 在 postProcessAfterInitialization 返回代理
                  JDK 动态代理（有接口）/ CGLIB（无接口或 proxyTargetClass=true，Spring Boot 默认 CGLIB）
                  @Transactional 由 TransactionInterceptor 实现，底层 PlatformTransactionManager + ThreadLocal 绑定 Connection
                事务失效的经典场景（都是因为"绕过了代理"）：
                  同类内部方法自调用 this.method()、方法不是 public、异常被 catch 吞掉、抛出受检异常（默认只回滚 RuntimeException/Error）、
                  多线程中调用（ThreadLocal 隔离）、数据库引擎不支持事务（MyISAM）
                """);
    }
}
