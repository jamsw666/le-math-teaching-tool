package com.javalearning.modern;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识点 6-6：Record 与 Sealed 类（Java 16 / 17）。
 *
 * <p>重点：record 作为透明不可变数据载体（自动 equals/hashCode/toString/访问器）、紧凑构造器校验、
 * record 实现接口与泛型 record；sealed 限制继承层次（permits），子类必须 final / sealed / non-sealed；
 * 二者组合即"代数数据类型"，为 switch 模式匹配提供穷尽性检查。
 */
public class RecordSealedDemo {

    /** 密封接口：形状只能是这三种，编译器据此检查 switch 是否穷尽。 */
    sealed interface Shape permits Circle, Rectangle, Triangle { }

    record Circle(double radius) implements Shape {
        Circle {
            if (radius <= 0) throw new IllegalArgumentException("半径必须为正");
        }
    }

    record Rectangle(double width, double height) implements Shape { }

    /** non-sealed 重新开放继承。 */
    non-sealed static class Triangle implements Shape {
        final double base, height;

        Triangle(double base, double height) {
            this.base = base;
            this.height = height;
        }
    }

    /** 泛型 record + 静态工厂 + 自定义方法。 */
    record Pair<A, B>(A first, B second) {
        static <A, B> Pair<A, B> of(A a, B b) {
            return new Pair<>(a, b);
        }

        Pair<B, A> swap() {
            return new Pair<>(second, first);
        }
    }

    /** 用 record 做 Map 的复合 key（正确的 equals/hashCode 自动生成）。 */
    record CityDate(String city, String date) { }

    static double area(Shape shape) {
        return switch (shape) {              // 密封类型 + 模式匹配：没有 default 也能编译，漏掉一种会报错
            case Circle c -> Math.PI * c.radius() * c.radius();
            case Rectangle r -> r.width() * r.height();
            case Triangle t -> t.base * t.height / 2;
        };
    }

    public static void main(String[] args) {
        System.out.println("== Record ==");
        Pair<String, Integer> p = Pair.of("age", 30);
        System.out.println("  " + p + " swap -> " + p.swap() + "，访问器 p.first()=" + p.first());
        System.out.println("  值相等: " + p.equals(Pair.of("age", 30)) + "，hashCode 相等: " + (p.hashCode() == Pair.of("age", 30).hashCode()));
        Map<CityDate, Integer> sales = Map.of(new CityDate("SH", "2024-01-01"), 100);
        System.out.println("  record 作复合 key 查找: " + sales.get(new CityDate("SH", "2024-01-01")));
        try {
            new Circle(-1);
        } catch (IllegalArgumentException e) {
            System.out.println("  紧凑构造器校验: " + e.getMessage());
        }
        System.out.println("  record 是隐式 final 的，字段 private final，不能再声明实例字段，可以有静态字段、静态方法、实例方法、嵌套类型");
        System.out.println("  反射支持: " + Circle.class.isRecord() + "，组件: " + List.of(Circle.class.getRecordComponents()));

        System.out.println("\n== Sealed + 模式匹配 ==");
        List<Shape> shapes = List.of(new Circle(1), new Rectangle(2, 3), new Triangle(4, 5));
        System.out.println("  面积: " + shapes.stream().map(s -> String.format("%s=%.2f", s.getClass().getSimpleName(), area(s))).collect(Collectors.joining(", ")));
        System.out.println("  Shape 允许的子类: " + List.of(Shape.class.getPermittedSubclasses()).stream().map(Class::getSimpleName).toList());
        System.out.println("""

                sealed 规则：
                  permits 列出全部直接子类（同一文件内可省略 permits 由编译器推断）
                  子类必须是 final（record 隐式 final）、sealed（继续限制）或 non-sealed（重新开放）之一
                  子类必须与父类在同一模块（未命名模块则同一包）
                用途：
                  领域建模（订单状态、支付方式、表达式树）替代"枚举 + 携带不同数据"的尴尬
                  配合 switch 模式匹配获得编译期穷尽检查——新增一种子类，所有漏处理的 switch 立刻报错
                  这就是函数式语言里的 ADT（代数数据类型）：record = 积类型，sealed = 和类型
                """);
    }
}
