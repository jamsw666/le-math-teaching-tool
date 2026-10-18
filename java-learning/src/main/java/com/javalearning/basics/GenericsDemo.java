package com.javalearning.basics;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 知识点 1-6：泛型与类型擦除。
 *
 * <p>重点：泛型类/方法、通配符 PECS（Producer Extends, Consumer Super）、
 * 类型擦除带来的限制（不能 new T()、不能用基本类型、运行时无法区分 List&lt;String&gt; 与 List&lt;Integer&gt;）、桥方法。
 */
public class GenericsDemo {

    /** 泛型类：有界类型参数。 */
    static class Box<T extends Comparable<T>> {
        private T value;

        void set(T value) { this.value = value; }
        T get() { return value; }

        boolean isGreaterThan(Box<T> other) {
            return value.compareTo(other.value) > 0;
        }
    }

    /** 泛型方法：类型参数声明在返回值之前。 */
    static <T> T firstOrDefault(List<T> list, T defaultValue) {
        return list.isEmpty() ? defaultValue : list.get(0);
    }

    /** PECS：读取用 extends（生产者）。 */
    static double sum(List<? extends Number> numbers) {
        double total = 0;
        for (Number n : numbers) total += n.doubleValue();
        // numbers.add(1); // 编译错误：不知道具体子类型，不能写入
        return total;
    }

    /** PECS：写入用 super（消费者）。 */
    static void fillIntegers(List<? super Integer> target) {
        target.add(1);
        target.add(2);
        // Integer i = target.get(0); // 编译错误：读取只能得到 Object
    }

    interface Node<T> {
        T value();
    }

    static class StringNode implements Node<String> {
        @Override
        public String value() { // 编译器会生成桥方法 Object value()
            return "bridge";
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 泛型类与方法 ==");
        Box<Integer> a = new Box<>(); a.set(10);
        Box<Integer> b = new Box<>(); b.set(7);
        System.out.println("10 > 7 ? " + a.isGreaterThan(b));
        System.out.println("firstOrDefault(empty, -1) = " + firstOrDefault(new ArrayList<Integer>(), -1));

        System.out.println("\n== PECS 通配符 ==");
        System.out.println("sum([1, 2.5, 3L]) = " + sum(List.of(1, 2.5, 3L)));
        List<Number> numbers = new ArrayList<>();
        fillIntegers(numbers);
        System.out.println("fillIntegers(List<Number>) -> " + numbers);
        List<Integer> ints = new ArrayList<>(List.of(3, 1, 2));
        ints.sort(Comparator.<Integer>naturalOrder().reversed());
        System.out.println("Comparator<? super T> 允许传入父类型比较器: " + ints);

        System.out.println("\n== 类型擦除 ==");
        List<String> strings = new ArrayList<>();
        List<Integer> integers = new ArrayList<>();
        System.out.println("List<String>.class == List<Integer>.class ? "
                + (strings.getClass() == integers.getClass()) + "（运行时泛型信息被擦除）");

        // 利用擦除"绕过"编译检查（仅演示，禁止在生产使用）
        List raw = strings;
        raw.add(42);
        Object smuggled = raw.get(0);
        System.out.println("通过原始类型塞入 Integer 后，列表里实际放着 " + smuggled.getClass().getSimpleName());
        try {
            String s = strings.get(0); // 编译器在此处插入 checkcast String
            System.out.println(s);
        } catch (ClassCastException e) {
            System.out.println("取出并赋给 String 时才抛 ClassCastException（堆污染，错误延迟暴露）");
        }

        System.out.println("\n== 桥方法 ==");
        for (Method m : StringNode.class.getDeclaredMethods()) {
            System.out.println("  " + m.getReturnType().getSimpleName() + " value() bridge=" + m.isBridge());
        }
    }
}
