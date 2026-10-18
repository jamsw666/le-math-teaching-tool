package com.javalearning.basics;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BinaryOperator;

/**
 * 知识点 1-8：枚举、Record 与内部类。
 *
 * <p>重点：枚举是天然单例且可实现接口、携带行为；Record 是不可变数据载体（自动生成构造器、访问器、equals/hashCode/toString）；
 * 四种内部类（成员、静态、局部、匿名）及其对外部类引用的差异。
 */
public class EnumRecordInnerClassDemo {

    /** 每个枚举常量可以有自己的实现——策略模式的最简写法。 */
    enum Operation implements BinaryOperator<Integer> {
        ADD("+") {
            public Integer apply(Integer a, Integer b) { return a + b; }
        },
        MUL("*") {
            public Integer apply(Integer a, Integer b) { return a * b; }
        };

        private final String symbol;

        Operation(String symbol) {
            this.symbol = symbol;
        }

        String symbol() {
            return symbol;
        }
    }

    enum Status { ACTIVE, FROZEN, CLOSED }

    /** Record：紧凑构造器用于校验，可添加方法但不能添加实例字段。 */
    record Point(int x, int y) {
        Point {
            if (x < 0 || y < 0) throw new IllegalArgumentException("坐标不能为负");
        }

        double distanceToOrigin() {
            return Math.sqrt(x * x + y * y);
        }

        static Point origin() {
            return new Point(0, 0);
        }
    }

    private int counter = 0;

    /** 成员内部类：隐式持有外部类引用（Outer.this），可访问外部私有成员。 */
    class Counter {
        int incrementOuter() {
            return ++counter;
        }
    }

    /** 静态内部类：不持有外部引用，推荐默认使用，避免内存泄漏。 */
    static class Helper {
        static String help() {
            return "静态内部类不依赖外部实例";
        }
    }

    public static void main(String[] args) {
        System.out.println("== 枚举 ==");
        for (Operation op : Operation.values()) {
            System.out.printf("  3 %s 4 = %d (ordinal=%d)%n", op.symbol(), op.apply(3, 4), op.ordinal());
        }
        System.out.println("  valueOf(\"ADD\") == ADD ? " + (Operation.valueOf("ADD") == Operation.ADD) + "（枚举可直接用 == 比较）");
        Map<Status, Integer> counts = new EnumMap<>(Status.class);
        counts.put(Status.ACTIVE, 10);
        System.out.println("  EnumMap 内部用数组按 ordinal 索引，比 HashMap 更快更省内存: " + counts);

        System.out.println("\n== Record ==");
        Point p = new Point(3, 4);
        System.out.println("  " + p + "，距原点 " + p.distanceToOrigin());
        System.out.println("  equals 按值比较: " + p.equals(new Point(3, 4)) + "，访问器 p.x() = " + p.x());
        try {
            new Point(-1, 0);
        } catch (IllegalArgumentException e) {
            System.out.println("  紧凑构造器校验: " + e.getMessage());
        }

        System.out.println("\n== 内部类 ==");
        EnumRecordInnerClassDemo outer = new EnumRecordInnerClassDemo();
        EnumRecordInnerClassDemo.Counter c = outer.new Counter();
        c.incrementOuter();
        System.out.println("  成员内部类修改外部 counter = " + outer.counter);
        System.out.println("  " + Helper.help());

        // 局部类与匿名类只能捕获 effectively final 的局部变量
        int base = 100;
        Runnable anonymous = new Runnable() {
            @Override
            public void run() {
                System.out.println("  匿名内部类捕获局部变量 base = " + base);
            }
        };
        anonymous.run();
        System.out.println("  Lambda 不会产生新的 Class 文件，而匿名类会生成 Outer$1.class");
    }
}
