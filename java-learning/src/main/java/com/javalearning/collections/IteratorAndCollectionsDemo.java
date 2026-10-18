package com.javalearning.collections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 知识点 2-5：Iterator 与 Collections 工具类。
 *
 * <p>重点：Iterable/Iterator 契约（for-each 的语法糖本质）、自定义可迭代类型、
 * Collections 常用静态方法（排序、二分、不可变包装、同步包装、洗牌、频次统计）。
 */
public class IteratorAndCollectionsDemo {

    /** 自定义范围类型：实现 Iterable 即可用于 for-each。 */
    record Range(int startInclusive, int endExclusive) implements Iterable<Integer> {
        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<>() {
                int cursor = startInclusive;

                @Override
                public boolean hasNext() {
                    return cursor < endExclusive;
                }

                @Override
                public Integer next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return cursor++;
                }
            };
        }
    }

    record Student(String name, int score) { }

    public static void main(String[] args) {
        System.out.println("== 自定义 Iterable ==");
        StringBuilder sb = new StringBuilder();
        for (int i : new Range(1, 6)) sb.append(i).append(' ');
        System.out.println("for (int i : new Range(1, 6)) -> " + sb);
        System.out.println("for-each 编译后等价于: Iterator it = range.iterator(); while (it.hasNext()) { it.next(); }");

        System.out.println("\n== Collections 工具类 ==");
        List<Student> students = new ArrayList<>(List.of(
                new Student("Tom", 88), new Student("Amy", 95), new Student("Bob", 88), new Student("Cat", 70)));
        students.sort(Comparator.comparingInt(Student::score).reversed().thenComparing(Student::name));
        System.out.println("多字段排序（分数降序、同分按名字）: " + students);
        System.out.println("Collections.sort 是稳定排序（TimSort），对象数组排序亦然；基本类型数组用双轴快排");

        List<Integer> nums = new ArrayList<>(List.of(5, 3, 9, 1, 7));
        Collections.sort(nums);
        System.out.println("binarySearch(7) 在已排序 " + nums + " 中下标 = " + Collections.binarySearch(nums, 7));
        System.out.println("max=" + Collections.max(nums) + " min=" + Collections.min(nums)
                + " frequency(3)=" + Collections.frequency(nums, 3));
        Collections.reverse(nums);
        System.out.println("reverse -> " + nums);
        Collections.shuffle(nums, new java.util.Random(42));
        System.out.println("shuffle(seed=42) -> " + nums);
        Collections.swap(nums, 0, 1);
        System.out.println("swap(0,1) -> " + nums + "，rotate 可循环移位，nCopies 生成重复元素列表");

        System.out.println("\n== 包装器 ==");
        List<Integer> readOnly = Collections.unmodifiableList(nums);
        try {
            readOnly.add(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("unmodifiableList 是只读视图（底层改动仍可见），List.copyOf 才是真正的快照");
        }
        List<Integer> sync = Collections.synchronizedList(new ArrayList<>());
        System.out.println("synchronizedList 每个方法加同一把锁，遍历时仍需手动 synchronized(list)；高并发用 CopyOnWriteArrayList: " + sync.getClass().getSimpleName());
        System.out.println("emptyList/singletonList 返回共享的不可变实例，用于避免返回 null");
    }
}
