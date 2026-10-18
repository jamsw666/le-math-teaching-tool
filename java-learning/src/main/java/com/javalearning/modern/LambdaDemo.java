package com.javalearning.modern;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * 知识点 6-1：Lambda 与函数式接口（Java 8）。
 *
 * <p>重点：函数式接口 = 只有一个抽象方法的接口（@FunctionalInterface）、四大内置接口族、
 * 方法引用的四种形式、Lambda 的变量捕获（effectively final）、this 的语义差异、
 * 函数组合（andThen / compose / and / or / negate）、Lambda 底层通过 invokedynamic 实现而非匿名类。
 */
public class LambdaDemo {

    @FunctionalInterface
    interface Validator<T> {
        boolean validate(T t);

        default Validator<T> and(Validator<T> other) {
            return t -> validate(t) && other.validate(t);
        }
    }

    record User(String name, int age) { }

    static User create(String name, int age) {
        return new User(name, age);
    }

    public static void main(String[] args) {
        builtinInterfaces();
        methodReferences();
        composition();
        captureRules();
    }

    static void builtinInterfaces() {
        System.out.println("== 四大内置函数式接口 ==");
        Supplier<User> supplier = () -> new User("default", 0);        // () -> T
        Consumer<User> consumer = u -> System.out.println("  消费: " + u); // T -> void
        Function<User, String> function = User::name;                     // T -> R
        Predicate<User> predicate = u -> u.age() >= 18;                   // T -> boolean
        UnaryOperator<String> upper = String::toUpperCase;                // T -> T
        BiFunction<String, Integer, User> factory = User::new;            // (T, U) -> R

        User u = factory.apply("alice", 20);
        consumer.accept(u);
        System.out.println("  Supplier 产出 " + supplier.get() + "，Function 取名 " + function.apply(u)
                + "，Predicate 成年 " + predicate.test(u) + "，UnaryOperator " + upper.apply("abc"));
        System.out.println("  基本类型特化版本（IntFunction、ToIntFunction、IntPredicate...）用于避免装箱");
    }

    static void methodReferences() {
        System.out.println("\n== 方法引用四种形式 ==");
        Function<String, Integer> parse = Integer::parseInt;              // 1. 静态方法  类::静态方法
        Function<String, String> trim = String::trim;                      // 2. 任意对象的实例方法  类::实例方法（第一个参数作为接收者）
        String prefix = ">> ";
        Function<String, String> concat = prefix::concat;                  // 3. 特定对象的实例方法  对象::方法
        Supplier<StringBuilder> ctor = StringBuilder::new;                 // 4. 构造器  类::new
        BiFunction<String, Integer, User> staticFactory = LambdaDemo::create;
        System.out.println("  " + parse.apply("42") + " | [" + trim.apply("  x  ") + "] | " + concat.apply("hi") + " | "
                + ctor.get().append("sb") + " | " + staticFactory.apply("bob", 3));
    }

    static void composition() {
        System.out.println("\n== 函数组合 ==");
        Function<Integer, Integer> plus2 = x -> x + 2;
        Function<Integer, Integer> times3 = x -> x * 3;
        System.out.println("  plus2.andThen(times3).apply(1) = " + plus2.andThen(times3).apply(1) + "  (1+2)*3");
        System.out.println("  plus2.compose(times3).apply(1) = " + plus2.compose(times3).apply(1) + "  1*3+2");

        Predicate<String> notEmpty = s -> !s.isEmpty();
        Predicate<String> shortStr = s -> s.length() < 5;
        List<String> words = List.of("", "java", "lambda", "fp");
        System.out.println("  非空且短: " + words.stream().filter(notEmpty.and(shortStr)).toList()
                + "，非空或短: " + words.stream().filter(notEmpty.negate().or(shortStr)).toList());

        Validator<User> adult = u -> u.age() >= 18;
        Validator<User> named = u -> !u.name().isBlank();
        System.out.println("  自定义函数式接口组合: " + adult.and(named).validate(new User("tom", 20)));
    }

    static void captureRules() {
        System.out.println("\n== 变量捕获与 this ==");
        int base = 10; // effectively final：之后不能再赋值
        Function<Integer, Integer> addBase = x -> x + base;
        // base = 11; // 取消注释会导致编译错误
        System.out.println("  捕获的局部变量必须是 effectively final: " + addBase.apply(5));

        int[] counter = {0};               // 用数组/对象"绕过"限制——注意这在并行流中不安全
        Runnable inc = () -> counter[0]++;
        inc.run();
        inc.run();
        System.out.println("  通过数组元素累加: " + counter[0] + "（并发场景请用 AtomicInteger）");

        new LambdaDemo().thisSemantics();
        System.out.println("  Lambda 不生成额外 class 文件，而是 invokedynamic + LambdaMetafactory 在运行时生成（见 BytecodeDemo）");
    }

    void thisSemantics() {
        Runnable lambda = () -> System.out.println("  Lambda 中 this = 外部类实例 " + this.getClass().getSimpleName());
        Runnable anonymous = new Runnable() {
            @Override
            public void run() {
                System.out.println("  匿名类中 this = 匿名类自身 " + this.getClass().getName());
            }
        };
        lambda.run();
        anonymous.run();
    }
}
