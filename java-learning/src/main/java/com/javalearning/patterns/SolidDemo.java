package com.javalearning.patterns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 10-4 SOLID 原则。
 *
 * <p>每条原则给一段“违反”与“遵守”的对照代码，并实际运行展示违反时的具体后果
 * （LSP 用经典的 正方形/矩形 问题，ISP 用“被迫实现不支持的方法”，DIP 用“可替换实现”）。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.patterns.SolidDemo
 */
public class SolidDemo {

    public static void main(String[] args) {
        srp();
        ocp();
        lsp();
        isp();
        dip();
        printSummary();
    }

    // ---------------- S：单一职责 ----------------
    /** 违反：一个类同时负责 业务计算 + 持久化 + 格式化输出，任一需求变化都要改它。 */
    static class ReportGod {
        String generate(List<BigDecimal> sales) {
            BigDecimal total = sales.stream().reduce(BigDecimal.ZERO, BigDecimal::add); // 计算
            String html = "<h1>Total: " + total + "</h1>";                               // 渲染
            System.out.println("      [ReportGod] 保存到数据库...");                        // 持久化
            return html;
        }
    }

    /** 遵守：按“变化的原因”拆分。 */
    static class SalesCalculator {
        BigDecimal total(List<BigDecimal> sales) { return sales.stream().reduce(BigDecimal.ZERO, BigDecimal::add); }
    }
    static class HtmlRenderer {
        String render(BigDecimal total) { return "<h1>Total: " + total + "</h1>"; }
    }
    static class ReportRepository {
        void save(String report) { System.out.println("      [Repository] 保存 " + report); }
    }

    static void srp() {
        System.out.println("== S 单一职责：一个类只有一个引起变化的原因 ==");
        List<BigDecimal> sales = List.of(new BigDecimal("10"), new BigDecimal("20.5"));
        System.out.println("  违反: " + new ReportGod().generate(sales));
        BigDecimal total = new SalesCalculator().total(sales);
        String html = new HtmlRenderer().render(total);
        new ReportRepository().save(html);
        System.out.println("  遵守: 计算 / 渲染 / 持久化三个类，换成 PDF 输出或换存储都互不影响，且各自可单独测试");
    }

    // ---------------- O：开闭 ----------------
    /** 违反：每加一种图形都要改这个方法。 */
    static double areaSwitch(Object shape) {
        if (shape instanceof double[] circle) return Math.PI * circle[0] * circle[0];
        if (shape instanceof int[] rect) return rect[0] * rect[1];
        throw new IllegalArgumentException("unknown shape");
    }

    /** 遵守：对扩展开放（新增实现），对修改关闭（计算总面积的代码不变）。 */
    interface Shape {
        double area();
    }
    record Circle(double r) implements Shape { public double area() { return Math.PI * r * r; } }
    record Rect(double w, double h) implements Shape { public double area() { return w * h; } }
    record Triangle(double b, double h) implements Shape { public double area() { return b * h / 2; } } // 新增，不改任何旧代码

    static double totalArea(List<Shape> shapes) { return shapes.stream().mapToDouble(Shape::area).sum(); }

    static void ocp() {
        System.out.println("\n== O 开闭：对扩展开放，对修改关闭 ==");
        System.out.printf("  违反: areaSwitch 只认识圆和矩形 = %.2f + %.2f，加三角形必须改它%n", areaSwitch(new double[]{1}), areaSwitch(new int[]{2, 3}));
        System.out.printf("  遵守: totalArea = %.2f（新增 Triangle 只是多一个类）%n", totalArea(List.of(new Circle(1), new Rect(2, 3), new Triangle(4, 5))));
        System.out.println("  手段：抽象 + 多态、策略/模板方法、插件注册表、Spring 的 @ConditionalOn* 扩展点。注意：不是绝对不改，而是让“最常变化的方向”不改");
    }

    // ---------------- L：里氏替换 ----------------
    static class Rectangle {
        protected int w;
        protected int h;
        void setW(int w) { this.w = w; }
        void setH(int h) { this.h = h; }
        int area() { return w * h; }
    }

    /** 违反：正方形“是一种”矩形在数学上成立，但在“可独立设宽高”这个契约上不成立。 */
    static class Square extends Rectangle {
        @Override void setW(int w) { this.w = w; this.h = w; }
        @Override void setH(int h) { this.w = h; this.h = h; }
    }

    static void useRectangle(Rectangle r, String label) {
        r.setW(5);
        r.setH(4);
        System.out.printf("  %s: 设宽 5 高 4，期望面积 20，实际 %d %s%n", label, r.area(), r.area() == 20 ? "✔" : "✘ 子类破坏了父类的契约");
    }

    static void lsp() {
        System.out.println("\n== L 里氏替换：子类必须能替换父类而不破坏程序正确性 ==");
        useRectangle(new Rectangle(), "Rectangle");
        useRectangle(new Square(), "Square   ");
        System.out.println("  修正：不要让 Square 继承 Rectangle，二者都实现 Shape 接口且不可变（record Square(int side)）");
        System.out.println("  判断：子类不能加强前置条件、不能削弱后置条件、不能抛父类没声明的异常、不能让父类方法“失效”（如 UnsupportedOperationException）");
        System.out.println("  反例：java.util.Stack extends Vector（栈却能按下标访问）；Properties extends Hashtable<Object,Object>");
    }

    // ---------------- I：接口隔离 ----------------
    /** 违反：胖接口，打印机被迫实现不支持的传真。 */
    interface AllInOneMachine {
        void print();
        void scan();
        void fax();
    }
    static class CheapPrinter implements AllInOneMachine {
        public void print() { System.out.println("      打印中"); }
        public void scan() { throw new UnsupportedOperationException("不支持扫描"); }
        public void fax() { throw new UnsupportedOperationException("不支持传真"); }
    }

    /** 遵守：小而专的接口，按需组合。 */
    interface Printer { void print(); }
    interface Scanner { void scan(); }
    record SimplePrinter() implements Printer { public void print() { System.out.println("      打印中"); } }
    record MultiFunction() implements Printer, Scanner {
        public void print() { System.out.println("      打印中"); }
        public void scan() { System.out.println("      扫描中"); }
    }

    static void isp() {
        System.out.println("\n== I 接口隔离：客户端不应被迫依赖它不用的方法 ==");
        AllInOneMachine cheap = new CheapPrinter();
        cheap.print();
        try {
            cheap.fax();
        } catch (UnsupportedOperationException e) {
            System.out.println("  违反: 调用 fax -> " + e.getMessage() + "（编译期看不出来，运行时才炸）");
        }
        Printer p = new SimplePrinter();
        p.print();
        System.out.println("  遵守: 需要扫描的地方才依赖 Scanner，SimplePrinter 根本没有 fax 方法可被误调");
        System.out.println("  JDK 对照：Collection 接口的 add 在不可变集合上抛 UnsupportedOperationException 正是 ISP/LSP 妥协的历史包袱；Java 8 的函数式接口（单方法）是 ISP 的极致");
    }

    // ---------------- D：依赖倒置 ----------------
    /** 违反：高层 OrderService 直接 new 低层 MySQL 实现，无法替换、无法单测。 */
    static class MysqlOrderDao {
        void save(String order) { System.out.println("      [MySQL] INSERT " + order); }
    }
    static class TightOrderService {
        private final MysqlOrderDao dao = new MysqlOrderDao(); // 依赖具体
        void place(String o) { dao.save(o); }
    }

    /** 遵守：高层定义抽象，低层实现它，依赖方向“倒置”为低层依赖高层的抽象。 */
    interface OrderRepository {
        void save(String order);
    }
    static class LooseOrderService {
        private final OrderRepository repo;
        LooseOrderService(OrderRepository repo) { this.repo = repo; } // 构造器注入
        void place(String o) { repo.save(o); }
    }

    static void dip() {
        System.out.println("\n== D 依赖倒置：高层不依赖低层，二者都依赖抽象 ==");
        new TightOrderService().place("order-1");
        System.out.println("  违反: 想换 MongoDB 或在测试里用内存实现都做不到");
        List<String> memory = new ArrayList<>();
        new LooseOrderService(memory::add).place("order-2");
        new LooseOrderService(o -> System.out.println("      [Mongo] insertOne " + o)).place("order-3");
        System.out.println("  遵守: 内存实现收到 " + memory + "；换 Mongo 实现只是传不同对象。Spring IoC 容器就是帮你做这件事的（依赖注入是 DIP 的实现手段）");
        System.out.println("  包结构体现：接口 OrderRepository 放在业务（domain）包，MySQL 实现放在基础设施包并依赖 domain —— 这正是六边形/整洁架构的依赖方向");
    }

    static void printSummary() {
        System.out.println("""

                一句话记忆：
                  S 一个类只做一件事        O 加功能靠新增不靠修改        L 子类别“耍赖”
                  I 接口要小而专            D 面向接口编程、依赖注入
                配套原则：
                  DRY 不重复（但别为了去重制造错误抽象）| KISS 简单 | YAGNI 不做用不到的 | 迪米特法则（只和直接朋友说话，a.getB().getC().doX() 是坏味道）
                  组合优于继承（Effective Java 第 18 条）| 高内聚低耦合 | 面向接口编程
                警惕过度设计：三行 if 不需要策略模式；只有一个实现的接口不一定要抽（但对外部依赖——DB、HTTP、时间——几乎总值得抽）
                参考：《敏捷软件开发：原则、模式与实践》（Robert C. Martin）、《Clean Architecture》、《Effective Java》
                """);
    }
}
