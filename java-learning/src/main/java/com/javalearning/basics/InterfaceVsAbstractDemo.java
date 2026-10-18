package com.javalearning.basics;

/**
 * 知识点 1-4：接口与抽象类。
 *
 * <p>重点：接口定义"能做什么"（能力），抽象类定义"是什么"（骨架）；
 * Java 8 默认方法、静态方法，Java 9 私有方法；菱形冲突的解决。
 */
public class InterfaceVsAbstractDemo {

    interface Flyable {
        int MAX_ALTITUDE = 10_000; // 隐式 public static final

        void fly();                 // 隐式 public abstract

        default String status() {   // Java 8：默认方法，便于接口演进不破坏实现类
            return name() + " 正在飞行，" + log("status");
        }

        String name();

        static Flyable of(String name) { // Java 8：接口静态方法（工厂）
            return new Flyable() {
                public void fly() { System.out.println(name + " 起飞"); }
                public String name() { return name; }
            };
        }

        private String log(String action) { // Java 9：私有方法，供默认方法复用
            return "[log:" + action + "]";
        }
    }

    interface Swimmable {
        default String status() {
            return name() + " 正在游泳";
        }

        String name();
    }

    /** 抽象类：有状态、有构造器、可以有非 public 成员，适合"模板 + 共享状态"。 */
    static abstract class Animal {
        protected final String name;
        private int energy = 100;

        protected Animal(String name) {
            this.name = name;
        }

        abstract String sound();

        final void speak() {
            energy -= 10;
            System.out.println(name + " 说: " + sound() + "（剩余体力 " + energy + "）");
        }
    }

    /** 单继承 + 多实现；两个接口的同名默认方法必须显式解决菱形冲突。 */
    static class Duck extends Animal implements Flyable, Swimmable {
        Duck() {
            super("鸭子");
        }

        @Override
        String sound() {
            return "嘎嘎";
        }

        @Override
        public void fly() {
            System.out.println(name + " 扑腾着飞起来了");
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String status() {
            // 用 接口名.super.方法() 指定采用哪一个
            return Flyable.super.status() + " & " + Swimmable.super.status();
        }
    }

    public static void main(String[] args) {
        Duck duck = new Duck();
        duck.speak();
        duck.fly();
        System.out.println(duck.status());
        System.out.println("接口常量 MAX_ALTITUDE = " + Flyable.MAX_ALTITUDE);
        Flyable.of("纸飞机").fly();

        System.out.println("""

                选型口诀：
                  - 需要共享字段/构造器/受保护方法 -> 抽象类
                  - 只描述能力、希望被多个无关类型实现 -> 接口
                  - 两者可以组合：抽象类实现接口并提供骨架（如 AbstractList 实现 List）
                """);
    }
}
