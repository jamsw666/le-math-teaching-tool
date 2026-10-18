package com.javalearning.basics;

/**
 * 知识点 1-1：基本语法与数据类型。
 *
 * <p>重点：8 种基本类型的取值范围、自动装箱/拆箱、类型提升、精度丢失与整数溢出。
 */
public class SyntaxAndTypesDemo {

    public static void main(String[] args) {
        primitiveRanges();
        boxingPitfalls();
        numericPromotion();
        floatingPointPrecision();
    }

    static void primitiveRanges() {
        System.out.println("== 基本类型取值范围 ==");
        System.out.printf("byte   : %d ~ %d (1 字节)%n", Byte.MIN_VALUE, Byte.MAX_VALUE);
        System.out.printf("short  : %d ~ %d (2 字节)%n", Short.MIN_VALUE, Short.MAX_VALUE);
        System.out.printf("int    : %d ~ %d (4 字节)%n", Integer.MIN_VALUE, Integer.MAX_VALUE);
        System.out.printf("long   : %d ~ %d (8 字节)%n", Long.MIN_VALUE, Long.MAX_VALUE);
        System.out.printf("char   : %d ~ %d (2 字节, 无符号 UTF-16)%n", (int) Character.MIN_VALUE, (int) Character.MAX_VALUE);
        System.out.printf("float  : 约 7 位有效数字, double: 约 15~16 位%n");

        int overflow = Integer.MAX_VALUE + 1;
        System.out.println("Integer.MAX_VALUE + 1 = " + overflow + "  <- 整数溢出不会抛异常，而是回绕");
        System.out.println("Math.addExact 会抛出 ArithmeticException 来避免悄悄溢出");
    }

    static void boxingPitfalls() {
        System.out.println("\n== 自动装箱陷阱 ==");
        Integer a = 127, b = 127;
        Integer c = 128, d = 128;
        System.out.println("127 == 127 ? " + (a == b) + "  (IntegerCache 缓存 -128~127，引用相同)");
        System.out.println("128 == 128 ? " + (c == d) + "  (超出缓存范围，是两个不同对象)");
        System.out.println("128.equals(128) ? " + c.equals(d) + "  <- 比较包装类型请用 equals");

        Integer nullable = null;
        try {
            int unboxed = nullable; // 自动拆箱触发 NPE
            System.out.println(unboxed);
        } catch (NullPointerException e) {
            System.out.println("null 自动拆箱会抛出 NullPointerException");
        }
    }

    static void numericPromotion() {
        System.out.println("\n== 类型提升 ==");
        byte x = 10;
        byte y = 20;
        // byte + byte 会被提升为 int，所以需要强转
        byte sum = (byte) (x + y);
        System.out.println("byte + byte -> int，需要强转: " + sum);

        int i = 7;
        double result = i / 2;      // 先整除再提升
        double precise = i / 2.0;   // 有 double 参与则按浮点运算
        System.out.println("7 / 2 = " + result + "，7 / 2.0 = " + precise);

        char ch = 'A';
        ch += 1; // 复合赋值自带强转
        System.out.println("'A' + 1 = " + ch);
    }

    static void floatingPointPrecision() {
        System.out.println("\n== 浮点精度 ==");
        System.out.println("0.1 + 0.2 = " + (0.1 + 0.2) + "  <- 二进制无法精确表示 0.1");
        System.out.println("金额计算请用 BigDecimal: "
                + new java.math.BigDecimal("0.1").add(new java.math.BigDecimal("0.2")));
        System.out.println("注意 new BigDecimal(0.1) = " + new java.math.BigDecimal(0.1)
                + "，应使用字符串构造或 BigDecimal.valueOf");
    }
}
