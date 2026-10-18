package com.javalearning.collections;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 知识点 2-6：Stream API 与 Collectors。
 *
 * <p>重点：惰性求值与短路、中间操作 vs 终止操作、常用 Collectors（groupingBy / partitioningBy / joining / toMap）、
 * flatMap、并行流的适用场景与陷阱、Stream 只能消费一次。
 */
public class StreamDemo {

    record Order(String customer, String category, double amount) { }

    static final List<Order> ORDERS = List.of(
            new Order("Alice", "图书", 120.0),
            new Order("Bob", "电子", 2500.0),
            new Order("Alice", "电子", 800.0),
            new Order("Cathy", "图书", 60.0),
            new Order("Bob", "图书", 45.0),
            new Order("Alice", "食品", 30.0));

    public static void main(String[] args) {
        lazyAndShortCircuit();
        collectors();
        flatMapAndPrimitive();
        parallelPitfalls();
    }

    static void lazyAndShortCircuit() {
        System.out.println("== 惰性求值 ==");
        Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5, 6)
                .filter(n -> {
                    System.out.println("  filter " + n);
                    return n % 2 == 0;
                })
                .map(n -> {
                    System.out.println("  map " + n);
                    return n * 10;
                });
        System.out.println("中间操作声明完毕，此时什么都没执行");
        Optional<Integer> first = stream.findFirst();
        System.out.println("findFirst 触发执行并短路，结果 = " + first.orElse(-1) + "（只处理到第一个符合的元素）");
        try {
            stream.count();
        } catch (IllegalStateException e) {
            System.out.println("流只能消费一次: " + e.getMessage());
        }
    }

    static void collectors() {
        System.out.println("\n== Collectors ==");
        Map<String, Double> totalByCustomer = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer, Collectors.summingDouble(Order::amount)));
        System.out.println("每个客户消费总额: " + totalByCustomer);

        Map<String, Long> countByCategory = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::category, Collectors.counting()));
        System.out.println("每类订单数: " + countByCategory);

        Map<Boolean, List<String>> bigOrders = ORDERS.stream()
                .collect(Collectors.partitioningBy(o -> o.amount() >= 100,
                        Collectors.mapping(o -> o.customer() + ":" + o.amount(), Collectors.toList())));
        System.out.println("按是否 >= 100 分区: " + bigOrders);

        String names = ORDERS.stream().map(Order::customer).distinct().sorted().collect(Collectors.joining(", ", "[", "]"));
        System.out.println("去重排序后拼接: " + names);

        Map<String, Double> maxByCustomer = ORDERS.stream()
                .collect(Collectors.toMap(Order::customer, Order::amount, Math::max));
        System.out.println("toMap 必须处理 key 冲突（第三个参数），每人最大单笔: " + maxByCustomer);

        var stats = ORDERS.stream().mapToDouble(Order::amount).summaryStatistics();
        System.out.printf("统计: count=%d sum=%.1f avg=%.1f max=%.1f%n", stats.getCount(), stats.getSum(), stats.getAverage(), stats.getMax());
    }

    static void flatMapAndPrimitive() {
        System.out.println("\n== flatMap 与基本类型流 ==");
        List<List<Integer>> nested = List.of(List.of(1, 2), List.of(3), List.of(4, 5));
        System.out.println("flatMap 展平: " + nested.stream().flatMap(List::stream).toList());
        System.out.println("IntStream.rangeClosed(1,5).sum() = " + IntStream.rangeClosed(1, 5).sum()
                + "，mapToInt 避免装箱，boxed() 转回对象流");
        System.out.println("reduce 求积: " + Stream.of(1, 2, 3, 4).reduce(1, (a, b) -> a * b));
        System.out.println("Java 16 的 toList() 返回不可变列表；需要可变请用 collect(Collectors.toList())");
    }

    static void parallelPitfalls() {
        System.out.println("\n== 并行流 ==");
        long t = System.nanoTime();
        long seq = IntStream.range(0, 5_000_000).filter(i -> i % 3 == 0).count();
        long seqTime = System.nanoTime() - t;
        t = System.nanoTime();
        long par = IntStream.range(0, 5_000_000).parallel().filter(i -> i % 3 == 0).count();
        long parTime = System.nanoTime() - t;
        System.out.printf("顺序 %.1f ms，并行 %.1f ms（结果 %d / %d，可用核心 %d）%n",
                seqTime / 1e6, parTime / 1e6, seq, par, Runtime.getRuntime().availableProcessors());
        System.out.println("并行流共用 ForkJoinPool.commonPool，不适合 IO 阻塞任务；有状态 lambda（修改外部集合）在并行下会出错");
        System.out.println("只有数据量大、CPU 密集、源可高效拆分（ArrayList/数组/range）时才考虑 parallel()");
    }
}
