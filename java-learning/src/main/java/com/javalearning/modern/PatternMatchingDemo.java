package com.javalearning.modern;

import java.util.List;

/**
 * 知识点 6-7：模式匹配（Java 16 instanceof，Java 21 switch 与 record 模式）。
 *
 * <p>重点：instanceof 类型模式消灭强转、switch 类型模式与 when 守卫、null 分支、
 * record 解构模式（含嵌套）、穷尽性检查、支配性规则（具体类型必须写在前面）；用表达式树求值展示表达力。
 */
public class PatternMatchingDemo {

    sealed interface Expr permits Num, Add, Mul, Neg { }
    record Num(double value) implements Expr { }
    record Add(Expr left, Expr right) implements Expr { }
    record Mul(Expr left, Expr right) implements Expr { }
    record Neg(Expr operand) implements Expr { }

    /** record 模式递归解构：解释器只需几行。 */
    static double eval(Expr e) {
        return switch (e) {
            case Num(double v) -> v;
            case Add(Expr l, Expr r) -> eval(l) + eval(r);
            case Mul(Expr l, Expr r) -> eval(l) * eval(r);
            case Neg(Expr inner) -> -eval(inner);
        };
    }

    /** 嵌套模式 + 守卫做代数化简。 */
    static Expr simplify(Expr e) {
        return switch (e) {
            case Add(Num(double a), Num(double b)) -> new Num(a + b);        // 常量折叠
            case Mul(Num(double z), Expr any) when z == 0 -> new Num(0);     // 0 * x = 0
            case Mul(Num(double one), Expr x) when one == 1 -> simplify(x);  // 1 * x = x
            case Neg(Neg(Expr x)) -> simplify(x);                            // --x = x
            case Add(Expr l, Expr r) -> new Add(simplify(l), simplify(r));
            case Mul(Expr l, Expr r) -> new Mul(simplify(l), simplify(r));
            case Neg(Expr x) -> new Neg(simplify(x));
            case Num n -> n;
        };
    }

    static String describe(Object o) {
        return switch (o) {
            case null -> "null 值（Java 21 允许 case null，否则 switch(null) 抛 NPE）";
            case Integer i when i > 100 -> "大整数 " + i;        // 守卫条件
            case Integer i -> "整数 " + i;
            case String s when s.isBlank() -> "空白字符串";
            case String s -> "字符串 \"" + s + "\" 长度 " + s.length();
            case int[] arr -> "int 数组 长度 " + arr.length;
            case List<?> list -> "列表 大小 " + list.size();
            default -> "其他类型 " + o.getClass().getSimpleName();
        };
    }

    public static void main(String[] args) {
        System.out.println("== instanceof 模式 ==");
        Object obj = "pattern";
        if (obj instanceof String s && s.length() > 3) {      // 绑定变量 s 在 && 右侧与 if 体内可用
            System.out.println("  无需强转直接使用: " + s.toUpperCase());
        }
        if (!(obj instanceof Integer n)) {
            System.out.println("  取反时绑定变量在 if 之后的作用域可用（流程作用域）");
        } else {
            System.out.println(n + 1);
        }

        System.out.println("\n== switch 类型模式与守卫 ==");
        for (Object o : new Object[]{42, 1000, "", "hello", new int[3], List.of(1, 2), 3.14, null}) {
            System.out.println("  " + describe(o));
        }

        System.out.println("\n== record 模式：表达式树 ==");
        // (1 + 2) * -(-(x)) 其中 x = 4  =>  3 * 4 = 12
        Expr expr = new Mul(new Add(new Num(1), new Num(2)), new Neg(new Neg(new Num(4))));
        System.out.println("  原式: " + expr);
        System.out.println("  求值: " + eval(expr));
        System.out.println("  化简: " + simplify(expr) + " = " + eval(simplify(expr)));
        System.out.println("  0 * (x + y) 化简: " + simplify(new Mul(new Num(0), new Add(new Num(5), new Num(6)))));

        System.out.println("""

                规则：
                  支配性：case Integer i 必须在 case Object o 之前，否则编译错误（后者支配前者）
                  穷尽性：switch 表达式必须覆盖所有情况；sealed 层次可省 default，Object 类型必须有 default
                  守卫 when 只在类型模式后使用；case null 可与 default 合写 case null, default ->
                  record 模式可以嵌套任意深度，也可用 var 省略组件类型：case Add(var l, var r)
                  Java 21 还提供无名模式 _（预览，22 正式）忽略不关心的组件
                适用：替代 Visitor 模式处理 AST / 事件 / 消息分发；Jackson 多态 JSON 解析后的处理；领域事件的 switch 分派
                """);
    }
}
