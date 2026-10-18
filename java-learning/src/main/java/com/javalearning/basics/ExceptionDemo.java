package com.javalearning.basics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * 知识点 1-5：异常处理机制。
 *
 * <p>重点：受检 vs 非受检异常、try-with-resources 与抑制异常、finally 的执行时机与返回值陷阱、
 * 自定义业务异常与异常链。
 */
public class ExceptionDemo {

    /** 自定义业务异常：携带错误码，便于统一处理。 */
    static class BusinessException extends RuntimeException {
        private final String code;

        BusinessException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    /** 实现 AutoCloseable 便于 try-with-resources 自动释放。 */
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
            System.out.println("打开资源 " + name);
        }

        void use() {
            throw new IllegalStateException("使用 " + name + " 时出错");
        }

        @Override
        public void close() {
            System.out.println("关闭资源 " + name);
            throw new IllegalStateException("关闭 " + name + " 时出错");
        }
    }

    public static void main(String[] args) {
        checkedVsUnchecked();
        tryWithResources();
        finallyPitfall();
        exceptionChaining();
    }

    static void checkedVsUnchecked() {
        System.out.println("== 受检 vs 非受检 ==");
        try (BufferedReader reader = new BufferedReader(new StringReader("hello"))) {
            System.out.println("读取: " + reader.readLine() + "（IOException 是受检异常，必须处理或声明）");
        } catch (IOException e) {
            System.out.println("IO 失败: " + e.getMessage());
        }
        try {
            Object o = "str";
            Integer i = (Integer) o;
            System.out.println(i);
        } catch (ClassCastException e) {
            System.out.println("ClassCastException 是非受检异常（RuntimeException 子类），编译器不强制处理");
        }
    }

    static void tryWithResources() {
        System.out.println("\n== try-with-resources 与抑制异常 ==");
        try (Resource a = new Resource("A"); Resource b = new Resource("B")) {
            b.use();
        } catch (Exception e) {
            System.out.println("主异常: " + e.getMessage());
            for (Throwable s : e.getSuppressed()) {
                System.out.println("  被抑制的异常: " + s.getMessage() + "（资源按打开的逆序关闭）");
            }
        }
    }

    @SuppressWarnings("finally")
    static int returnInFinally() {
        try {
            return 1;
        } finally {
            return 2; // 会吞掉 try 中的返回值和任何异常——生产代码禁止
        }
    }

    static void finallyPitfall() {
        System.out.println("\n== finally 陷阱 ==");
        System.out.println("finally 中 return 覆盖了 try 的返回值: " + returnInFinally());
        System.out.println("finally 一定执行（除非 System.exit 或 JVM 崩溃），但不要在其中 return");
    }

    static void exceptionChaining() {
        System.out.println("\n== 异常链：底层异常转译为业务异常 ==");
        try {
            try {
                Integer.parseInt("abc");
            } catch (NumberFormatException e) {
                throw new BusinessException("E1001", "订单号格式非法", e);
            }
        } catch (BusinessException e) {
            System.out.println("错误码 " + e.code() + ": " + e.getMessage() + "，根因: " + e.getCause().getClass().getSimpleName());
        }
    }
}
