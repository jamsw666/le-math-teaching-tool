package com.javalearning.jvm;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.spi.ToolProvider;

/**
 * 知识点 4-6：字节码与 javap。
 *
 * <p>重点：class 文件结构（魔数、版本、常量池、访问标志、字段/方法表、属性表）、
 * 基于操作数栈的指令集、i++ 与 ++i 以及 "i = i++" 的字节码解释、字符串拼接的 invokedynamic、
 * 使用 JDK 内置 ToolProvider 以编程方式调用 javap。
 */
public class BytecodeDemo {

    static int plusPlusPuzzle() {
        int i = 0;
        i = i++;   // iload_1 -> iinc 1,1 -> istore_1：先把旧值 0 压栈，再自增，最后用栈顶 0 覆盖 i
        return i;  // 结果 0
    }

    static String concat(String a, int b) {
        return a + b; // Java 9+ 编译为 invokedynamic makeConcatWithConstants
    }

    static int sum(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== i = i++ 的结果 ==");
        System.out.println("  plusPlusPuzzle() = " + plusPlusPuzzle());

        System.out.println("\n== class 文件头 ==");
        Path classFile = Path.of(BytecodeDemo.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve(BytecodeDemo.class.getName().replace('.', '/') + ".class");
        if (Files.exists(classFile)) {
            byte[] bytes = Files.readAllBytes(classFile);
            System.out.printf("  魔数: 0x%02X%02X%02X%02X（CAFEBABE）%n", bytes[0], bytes[1], bytes[2], bytes[3]);
            int minor = ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF);
            int major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
            System.out.println("  版本: major=" + major + " minor=" + minor + "（52=Java 8, 61=Java 17, 65=Java 21）");
            int cpCount = ((bytes[8] & 0xFF) << 8) | (bytes[9] & 0xFF);
            System.out.println("  常量池项数: " + (cpCount - 1) + "，文件大小 " + bytes.length + " 字节");
        }

        System.out.println("\n== 用 javap 反编译 sum / plusPlusPuzzle ==");
        ToolProvider javap = ToolProvider.findFirst("javap").orElse(null);
        if (javap == null) {
            System.out.println("  当前运行时没有 javap（可能是精简 JRE），请在命令行执行: javap -c -p " + BytecodeDemo.class.getName());
        } else {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            String classpath = classFile.getParent().getParent().getParent().getParent().toString();
            javap.run(new PrintStream(out), new PrintStream(err), "-c", "-p", "-cp", classpath, BytecodeDemo.class.getName());
            String text = out.toString();
            printMethod(text, "static int sum(int, int);");
            printMethod(text, "static int plusPlusPuzzle();");
            printMethod(text, "static java.lang.String concat(java.lang.String, int);");
        }

        System.out.println("""
                字节码要点：
                  JVM 是基于操作数栈的虚拟机：iload 入栈、iadd 弹出两个相加再入栈、ireturn 返回栈顶
                  指令前缀表示类型：i(int) l(long) f(float) d(double) a(引用)；iconst_0~5 / bipush / sipush / ldc 加载常量
                  invokestatic / invokespecial(构造器、私有、super) / invokevirtual(实例方法) / invokeinterface / invokedynamic(Lambda、字符串拼接)
                  每个方法的 Code 属性记录 max_stack、max_locals、异常表、LineNumberTable（栈轨迹行号来源）
                字节码工具：javap -v 查看常量池；ASM / ByteBuddy / Javassist 运行时生成或修改字节码（Spring CGLIB、Mockito、APM 探针）
                """);
    }

    static void printMethod(String javapOutput, String signature) {
        int start = javapOutput.indexOf(signature);
        if (start < 0) return;
        int end = javapOutput.indexOf("\n\n", start);
        System.out.println(javapOutput.substring(start, end < 0 ? javapOutput.length() : end).indent(2));
    }
}
