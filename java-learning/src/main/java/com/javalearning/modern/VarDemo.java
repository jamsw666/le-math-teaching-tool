package com.javalearning.modern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 知识点 6-4：var 局部类型推断（Java 10）。
 *
 * <p>重点：var 只是编译期语法糖，类型在声明处由初始化表达式推断，不是动态类型；
 * 适用与不适用的场景、与泛型菱形的交互、Java 11 允许在 Lambda 参数上使用 var（为了加注解）、
 * 可读性原则——右侧类型明显时用，否则写出类型。
 */
public class VarDemo {

    record Order(String id, double amount) { }

    public static void main(String[] args) {
        System.out.println("== 基本用法 ==");
        var message = "hello";                 // String
        var count = 42;                        // int（不是 Integer）
        var list = new ArrayList<String>();    // ArrayList<String>，注意不是 List<String>
        var map = new HashMap<String, List<Integer>>(); // 长泛型是 var 最有价值的地方
        list.add("a");
        map.put("k", List.of(1, 2));
        System.out.println("  message=" + message + " count=" + count + " list=" + list + " map=" + map);
        // message = 1; // 编译错误：类型已在声明时确定为 String，var 不是 dynamic

        System.out.println("\n== 推断结果可能和你想的不一样 ==");
        var diamond = new ArrayList<>();       // 推断为 ArrayList<Object>
        diamond.add(1);
        diamond.add("x");
        System.out.println("  var x = new ArrayList<>() 推断为 ArrayList<Object>: " + diamond);
        var num = 1L;                          // long
        var decimal = 1.0f;                    // float
        System.out.println("  1L -> long，1.0f -> float；要 List<String> 接口类型就显式写出来");

        System.out.println("\n== for 循环与 try-with-resources ==");
        var orders = List.of(new Order("A1", 99.5), new Order("B2", 10));
        var total = 0.0;
        for (var order : orders) {             // 元素类型 Order 一目了然
            total += order.amount();
        }
        System.out.println("  总额 = " + total);
        for (var i = 0; i < 2; i++) { }
        try (var in = new java.io.ByteArrayInputStream(new byte[0])) {
            System.out.println("  try-with-resources 中 var 可用，available=" + in.available());
        } catch (java.io.IOException ignored) { }

        System.out.println("\n== Lambda 参数上的 var（Java 11） ==");
        Function<String, Integer> len = (var s) -> s.length(); // 允许加注解如 (@NonNull var s)
        System.out.println("  (var s) -> s.length() 应用于 \"java\" = " + len.apply("java"));

        System.out.println("""

                不能使用 var 的地方：
                  字段、方法参数、返回值类型、没有初始化的声明（var x;）、初始化为 null（var x = null;）、
                  Lambda 或方法引用直接赋值（var f = () -> 1; 无法推断目标接口）、数组初始化（var a = {1, 2};）、复合声明（var a = 1, b = 2;）
                可读性建议（OpenJDK 风格指南）：
                  右侧是构造器 / 字面量 / 明确工厂时用 var；右侧是方法调用、返回类型不直观时写出类型
                  用好变量名补偿类型信息：var customers = repo.findAll(); 优于 var result = repo.findAll();
                  链式调用的中间变量用 var 可以避免写出冗长的中间类型（如 Map.Entry<String, List<Order>>）
                """);
        Map<String, Integer> m = Map.of("a", 1);
        for (var entry : m.entrySet()) {
            System.out.println("  entry 类型 Map.Entry<String,Integer> 由 var 省略: " + entry.getKey() + "=" + entry.getValue() + " (num=" + num + ", decimal=" + decimal + ")");
        }
    }
}
