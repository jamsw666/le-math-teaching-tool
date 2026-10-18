package com.javalearning.basics;

/**
 * 知识点 1-9：String 与字符串常量池。
 *
 * <p>重点：String 不可变的原因与好处、字面量进入常量池、new String 创建堆对象、intern()、
 * 编译期常量折叠、StringBuilder 与 + 的区别、Java 9 的紧凑字符串（Latin-1 / UTF-16）。
 */
public class StringPoolDemo {

    public static void main(String[] args) {
        System.out.println("== 常量池与 == ==");
        String a = "java";
        String b = "java";
        String c = new String("java");
        System.out.println("字面量 a == b ? " + (a == b) + "（同一常量池引用）");
        System.out.println("a == new String(\"java\") ? " + (a == c) + "（new 在堆上创建新对象）");
        System.out.println("a == c.intern() ? " + (a == c.intern()) + "（intern 返回池中引用）");

        System.out.println("\n== 编译期常量折叠 ==");
        final String x = "ja";
        String y = "va";
        String folded = "ja" + "va";     // 编译期折叠为 "java"
        String finalConcat = x + "va";   // x 是 final 常量，同样折叠
        String runtime = x + y;          // y 不是常量，运行期拼接，产生新对象
        System.out.println("\"ja\" + \"va\" == a ? " + (folded == a));
        System.out.println("final x + \"va\" == a ? " + (finalConcat == a));
        System.out.println("x + y == a ? " + (runtime == a) + "  <- 运行时拼接不进池");

        System.out.println("\n== 不可变性 ==");
        String s = "hello";
        String upper = s.toUpperCase();
        System.out.println("s.toUpperCase() 返回新对象，原字符串仍为: " + s + "，新对象: " + upper);
        System.out.println("不可变带来：线程安全、可缓存 hashCode、可安全用作 HashMap key、常量池共享");

        System.out.println("\n== 拼接性能 ==");
        long t0 = System.nanoTime();
        String plus = "";
        for (int i = 0; i < 20_000; i++) plus += i;   // 循环中 += 每次产生新对象
        long t1 = System.nanoTime();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20_000; i++) sb.append(i);
        long t2 = System.nanoTime();
        System.out.printf("循环 += 耗时 %.1f ms，StringBuilder 耗时 %.1f ms（长度均为 %d）%n",
                (t1 - t0) / 1e6, (t2 - t1) / 1e6, sb.length());
        System.out.println("单行 a + b + c 会被编译为 StringConcatFactory（Java 9+）或 StringBuilder，不必手写");

        System.out.println("\n== 常用 API ==");
        System.out.println("  \" trim \".strip() = [" + " trim ".strip() + "]，\"a,b,,c\".split(\",\") 长度 = " + "a,b,,c".split(",").length);
        System.out.println("  String.join(\"-\", \"a\",\"b\") = " + String.join("-", "a", "b") + "，\"ab\".repeat(3) = " + "ab".repeat(3));
        System.out.println("  \"中文\".getBytes(UTF_8).length = " + "中文".getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + "，length() = " + "中文".length() + "（length 是 UTF-16 单元数，不是字节数）");
    }
}
