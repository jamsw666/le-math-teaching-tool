package com.javalearning.basics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识点 1-7：注解与反射。
 *
 * <p>重点：自定义注解的元注解（@Retention 必须是 RUNTIME 才能被反射读取）、
 * 反射读取类/字段/方法、基于反射的参数校验框架雏形、JDK 动态代理。
 * 这是 Spring IoC/AOP、MyBatis Mapper、JUnit 等框架的底层基石。
 */
public class AnnotationReflectionDemo {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface NotBlank {
        String message() default "不能为空";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Range {
        int min();
        int max();
    }

    static class User {
        @NotBlank(message = "用户名不能为空")
        private String name;
        @Range(min = 0, max = 150)
        private int age;

        User(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    /** 一个 20 行的"校验框架"：这就是 Hibernate Validator 的核心思路。 */
    static List<String> validate(Object target) throws IllegalAccessException {
        List<String> errors = new ArrayList<>();
        for (Field f : target.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object value = f.get(target);
            NotBlank nb = f.getAnnotation(NotBlank.class);
            if (nb != null && (value == null || value.toString().isBlank())) {
                errors.add(f.getName() + ": " + nb.message());
            }
            Range range = f.getAnnotation(Range.class);
            if (range != null && value instanceof Integer i && (i < range.min() || i > range.max())) {
                errors.add(f.getName() + ": 必须在 [" + range.min() + ", " + range.max() + "] 之间");
            }
        }
        return errors;
    }

    interface GreetingService {
        String greet(String who);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 基于注解 + 反射的参数校验 ==");
        System.out.println(validate(new User("  ", 200)));
        System.out.println(validate(new User("Tom", 18)) + "  <- 空列表表示通过");

        System.out.println("\n== 反射调用方法 ==");
        Method m = String.class.getMethod("toUpperCase");
        System.out.println("反射调用 \"hello\".toUpperCase() = " + m.invoke("hello"));

        System.out.println("\n== JDK 动态代理（AOP 的基础） ==");
        GreetingService real = who -> "你好, " + who;
        InvocationHandler handler = (proxy, method, params) -> {
            long start = System.nanoTime();
            Object result = method.invoke(real, params);
            System.out.printf("  [切面] %s 耗时 %d ns%n", method.getName(), System.nanoTime() - start);
            return result;
        };
        GreetingService proxy = (GreetingService) Proxy.newProxyInstance(
                GreetingService.class.getClassLoader(), new Class<?>[]{GreetingService.class}, handler);
        System.out.println(proxy.greet("Java"));
        System.out.println("代理类名: " + proxy.getClass().getSimpleName() + "（JDK 代理要求目标实现接口；CGLIB 可代理类）");
    }
}
