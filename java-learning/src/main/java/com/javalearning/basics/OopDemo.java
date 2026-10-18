package com.javalearning.basics;

import java.util.List;

/**
 * 知识点 1-3：面向对象三大特性——封装、继承、多态。
 *
 * <p>重点：访问控制、构造链、方法重写 vs 重载、动态分派（运行时多态）、static 与字段不参与多态。
 */
public class OopDemo {

    /** 封装：字段私有，通过行为暴露能力而不是直接暴露数据。 */
    static class Account {
        private long balanceInCents;

        void deposit(long cents) {
            if (cents <= 0) throw new IllegalArgumentException("存款必须为正数");
            balanceInCents += cents;
        }

        long balance() {
            return balanceInCents;
        }
    }

    /** 继承：抽象出共性，子类复用并扩展。 */
    static abstract class Shape {
        protected final String name;
        static String kind = "Shape";

        Shape(String name) {
            this.name = name;
            System.out.println("  Shape 构造器执行: " + name);
        }

        abstract double area();

        /** 模板方法：公共流程固定，细节交给子类。 */
        String describe() {
            return String.format("%s 面积 = %.2f", name, area());
        }
    }

    static class Circle extends Shape {
        private final double r;
        static String kind = "Circle";

        Circle(double r) {
            super("圆");   // 构造链：先父后子
            this.r = r;
            System.out.println("  Circle 构造器执行");
        }

        @Override
        double area() {
            return Math.PI * r * r;
        }
    }

    static class Rect extends Shape {
        private final double w, h;

        Rect(double w, double h) {
            super("矩形");
            this.w = w;
            this.h = h;
        }

        @Override
        double area() {
            return w * h;
        }

        @Override
        String describe() {
            return super.describe() + "（重写了描述）";
        }
    }

    /** 重载：编译期根据静态类型选择；重写：运行期根据实际类型选择。 */
    static String whoAmI(Shape s) { return "Shape 版本"; }
    static String whoAmI(Circle c) { return "Circle 版本"; }

    public static void main(String[] args) {
        System.out.println("== 封装 ==");
        Account acc = new Account();
        acc.deposit(10_000);
        System.out.println("余额(分): " + acc.balance());

        System.out.println("\n== 继承与构造链 ==");
        Shape circle = new Circle(1.0);

        System.out.println("\n== 多态（动态分派） ==");
        List<Shape> shapes = List.of(circle, new Rect(2, 3));
        for (Shape s : shapes) {
            System.out.println(s.describe()); // 同一调用，不同行为
        }

        System.out.println("\n== 重载 vs 重写 ==");
        System.out.println("静态类型 Shape -> " + whoAmI(circle) + "（重载在编译期决定）");
        System.out.println("静态类型 Circle -> " + whoAmI((Circle) circle));

        System.out.println("\n== 静态成员与字段不参与多态 ==");
        System.out.println("((Shape) circle).kind = " + circle.kind + "  <- 字段按静态类型解析，被隐藏而非重写");
    }
}
