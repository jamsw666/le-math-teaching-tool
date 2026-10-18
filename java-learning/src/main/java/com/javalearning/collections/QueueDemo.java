package com.javalearning.collections;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * 知识点 2-4：Queue / Deque / PriorityQueue。
 *
 * <p>重点：offer/poll/peek 与 add/remove/element 的区别（失败返回特殊值 vs 抛异常）、
 * ArrayDeque 作为栈和队列的首选、PriorityQueue 的二叉堆实现与 Top-K 应用。
 */
public class QueueDemo {

    public static void main(String[] args) {
        queueApi();
        dequeAsStackAndQueue();
        priorityQueueTopK();
    }

    static void queueApi() {
        System.out.println("== Queue 两套 API ==");
        Queue<Integer> q = new ArrayDeque<>();
        q.offer(1);
        q.offer(2);
        System.out.println("peek = " + q.peek() + "，poll = " + q.poll() + "，poll = " + q.poll());
        System.out.println("空队列 poll() = " + q.poll() + "（返回 null）");
        try {
            q.remove();
        } catch (java.util.NoSuchElementException e) {
            System.out.println("空队列 remove() 抛 NoSuchElementException");
        }
        System.out.println("口诀：offer/poll/peek 返回特殊值；add/remove/element 抛异常；有界阻塞队列用 put/take 阻塞");
    }

    static void dequeAsStackAndQueue() {
        System.out.println("\n== ArrayDeque 做栈 ==");
        Deque<Character> stack = new ArrayDeque<>();
        String expr = "{[()]}(";
        boolean balanced = true;
        for (char c : expr.toCharArray()) {
            switch (c) {
                case '(', '[', '{' -> stack.push(c);
                case ')', ']', '}' -> {
                    if (stack.isEmpty()) { balanced = false; break; }
                    char open = stack.pop();
                    if ("([{".indexOf(open) != ")]}".indexOf(c)) balanced = false;
                }
                default -> { }
            }
        }
        balanced &= stack.isEmpty();
        System.out.println("括号匹配 \"" + expr + "\" -> " + balanced);
        System.out.println("java.util.Stack 继承 Vector（方法全加锁），已过时；栈请用 ArrayDeque");

        System.out.println("\n== ArrayDeque 做双端队列（滑动窗口最大值） ==");
        int[] nums = {1, 3, -1, -3, 5, 3, 6, 7};
        int k = 3;
        Deque<Integer> idx = new ArrayDeque<>();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < nums.length; i++) {
            while (!idx.isEmpty() && idx.peekFirst() <= i - k) idx.pollFirst();
            while (!idx.isEmpty() && nums[idx.peekLast()] < nums[i]) idx.pollLast();
            idx.offerLast(i);
            if (i >= k - 1) out.append(nums[idx.peekFirst()]).append(' ');
        }
        System.out.println("窗口 k=3 的最大值序列: " + out);
    }

    static void priorityQueueTopK() {
        System.out.println("\n== PriorityQueue（二叉堆） ==");
        int[] data = {7, 2, 9, 4, 11, 1, 8};
        int k = 3;
        PriorityQueue<Integer> minHeap = new PriorityQueue<>(); // 小顶堆，堆顶是当前 Top-K 中最小的
        for (int v : data) {
            minHeap.offer(v);
            if (minHeap.size() > k) minHeap.poll();
        }
        System.out.println("Top-" + k + " 大的元素: " + minHeap + "（遍历顺序不是排序顺序，只有 poll 才有序）");

        PriorityQueue<String> byLength = new PriorityQueue<>(Comparator.comparingInt(String::length).reversed());
        byLength.addAll(java.util.List.of("aa", "b", "cccc", "ddd"));
        StringBuilder sb = new StringBuilder();
        while (!byLength.isEmpty()) sb.append(byLength.poll()).append(' ');
        System.out.println("按长度降序 poll: " + sb);
        System.out.println("PriorityQueue 基于数组实现的完全二叉树，offer/poll O(log n)，peek O(1)，非线程安全（并发用 PriorityBlockingQueue）");
    }
}
