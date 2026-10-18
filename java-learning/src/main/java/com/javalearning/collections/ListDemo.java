package com.javalearning.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * 知识点 2-1：List —— ArrayList 与 LinkedList。
 *
 * <p>重点：底层结构（动态数组 vs 双向链表）、随机访问与中间插入的性能差异、
 * Arrays.asList / List.of 的不可变陷阱、遍历中删除元素的正确姿势。
 */
public class ListDemo {

    public static void main(String[] args) {
        performanceComparison();
        immutablePitfalls();
        removeWhileIterating();
    }

    static void performanceComparison() {
        System.out.println("== ArrayList vs LinkedList 性能 ==");
        int n = 100_000;
        List<Integer> arrayList = new ArrayList<>();
        List<Integer> linkedList = new LinkedList<>();

        long t = System.nanoTime();
        for (int i = 0; i < n; i++) arrayList.add(0, i);   // 头插：数组需要整体后移
        long arrayHeadInsert = System.nanoTime() - t;

        t = System.nanoTime();
        for (int i = 0; i < n; i++) linkedList.add(0, i);  // 头插：链表 O(1)
        long linkedHeadInsert = System.nanoTime() - t;

        t = System.nanoTime();
        long sum = 0;
        for (int i = 0; i < n; i += 100) sum += arrayList.get(i); // 随机访问 O(1)
        long arrayGet = System.nanoTime() - t;

        t = System.nanoTime();
        for (int i = 0; i < n; i += 100) sum += linkedList.get(i); // 随机访问 O(n)
        long linkedGet = System.nanoTime() - t;

        System.out.printf("头插 %d 次: ArrayList %.1f ms, LinkedList %.1f ms%n", n, arrayHeadInsert / 1e6, linkedHeadInsert / 1e6);
        System.out.printf("随机 get %d 次: ArrayList %.2f ms, LinkedList %.1f ms%n", n / 100, arrayGet / 1e6, linkedGet / 1e6);
        System.out.println("结论：绝大多数场景用 ArrayList；LinkedList 的优势只在于迭代器位置的插入删除，且常被 ArrayDeque 替代");
        System.out.println("ArrayList 扩容：默认容量 10，扩容为 1.5 倍（oldCapacity + (oldCapacity >> 1)），批量添加前可 ensureCapacity");
        System.out.println("(sum=" + sum + ")");
    }

    static void immutablePitfalls() {
        System.out.println("\n== 不可变列表陷阱 ==");
        String[] array = {"a", "b", "c"};
        List<String> view = Arrays.asList(array);
        view.set(0, "A");
        System.out.println("Arrays.asList 是数组的视图，set 会写穿到原数组: " + Arrays.toString(array));
        try {
            view.add("d");
        } catch (UnsupportedOperationException e) {
            System.out.println("Arrays.asList 不支持 add/remove（固定大小）");
        }
        List<String> immutable = List.of("x", "y");
        try {
            immutable.add("z");
        } catch (UnsupportedOperationException e) {
            System.out.println("List.of 完全不可变，且不允许 null 元素");
        }
        List<String> mutable = new ArrayList<>(immutable);
        mutable.add("z");
        System.out.println("需要修改请拷贝一份: " + mutable);
    }

    static void removeWhileIterating() {
        System.out.println("\n== 遍历中删除 ==");
        List<Integer> nums = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        try {
            for (Integer x : nums) {
                if (x % 2 == 0) nums.remove(x);
            }
        } catch (java.util.ConcurrentModificationException e) {
            System.out.println("foreach 中直接 remove 抛 ConcurrentModificationException");
        }
        nums = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        nums.removeIf(x -> x % 2 == 0);
        System.out.println("正确做法 1: removeIf -> " + nums);

        nums = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        for (var it = nums.iterator(); it.hasNext(); ) {
            if (it.next() % 2 == 0) it.remove();
        }
        System.out.println("正确做法 2: Iterator.remove -> " + nums);
    }
}
