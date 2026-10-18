package com.javalearning.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * 知识点 3-6：原子类与 CAS。
 *
 * <p>重点：CAS（Compare-And-Swap）的硬件原语本质与自旋、AtomicInteger 的 incrementAndGet 实现、
 * ABA 问题与 AtomicStampedReference、高并发下 LongAdder 的分段累加优于 AtomicLong、无锁栈。
 */
public class AtomicCasDemo {

    /** 手写 CAS 自旋累加，演示 AtomicInteger.incrementAndGet 的原理。 */
    static int casIncrement(AtomicInteger atomic) {
        for (;;) {
            int current = atomic.get();
            int next = current + 1;
            if (atomic.compareAndSet(current, next)) { // 期望值 == 当前值才写入，否则重试
                return next;
            }
        }
    }

    /** 基于 CAS 的无锁栈（Treiber Stack）。 */
    static class LockFreeStack<T> {
        private record Node<T>(T value, Node<T> next) { }

        private final AtomicReference<Node<T>> top = new AtomicReference<>();

        void push(T value) {
            Node<T> newHead;
            Node<T> oldHead;
            do {
                oldHead = top.get();
                newHead = new Node<>(value, oldHead);
            } while (!top.compareAndSet(oldHead, newHead));
        }

        T pop() {
            Node<T> oldHead;
            Node<T> newHead;
            do {
                oldHead = top.get();
                if (oldHead == null) return null;
                newHead = oldHead.next();
            } while (!top.compareAndSet(oldHead, newHead));
            return oldHead.value();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== CAS 自旋累加 ==");
        AtomicInteger atomic = new AtomicInteger();
        int threads = 8, loops = 10_000;
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                for (int j = 0; j < loops; j++) casIncrement(atomic);
                done.countDown();
            }).start();
        }
        done.await();
        System.out.println("  结果 = " + atomic.get() + "（期望 " + threads * loops + "），无锁但正确");
        System.out.println("  CAS 对应 x86 的 lock cmpxchg 指令，由 Unsafe/VarHandle 暴露；失败即自旋，高竞争时 CPU 空转");

        System.out.println("\n== ABA 问题 ==");
        AtomicReference<String> ref = new AtomicReference<>("A");
        ref.compareAndSet("A", "B");
        ref.compareAndSet("B", "A"); // 别的线程把值改回 A
        System.out.println("  值从 A->B->A 后，CAS(A, C) 仍成功 = " + ref.compareAndSet("A", "C") + "（察觉不到中间变化）");
        AtomicStampedReference<String> stamped = new AtomicStampedReference<>("A", 0);
        int stamp = stamped.getStamp();
        stamped.compareAndSet("A", "B", stamp, stamp + 1);
        stamped.compareAndSet("B", "A", stamp + 1, stamp + 2);
        System.out.println("  AtomicStampedReference 带版本号，CAS(A, C, 旧版本) = "
                + stamped.compareAndSet("A", "C", stamp, stamp + 1) + "（被拒绝，当前版本 " + stamped.getStamp() + "）");

        System.out.println("\n== LongAdder vs AtomicLong ==");
        java.util.concurrent.atomic.AtomicLong atomicLong = new java.util.concurrent.atomic.AtomicLong();
        LongAdder adder = new LongAdder();
        long t1 = time(() -> atomicLong.incrementAndGet(), 16, 200_000);
        long t2 = time(adder::increment, 16, 200_000);
        System.out.printf("  16 线程各 20 万次：AtomicLong %.1f ms，LongAdder %.1f ms（sum=%d）%n", t1 / 1e6, t2 / 1e6, adder.sum());
        System.out.println("  LongAdder 把热点分散到 Cell[] 数组，各线程累加自己的槽，sum() 时汇总；适合统计计数，不适合需要精确瞬时值的场景");

        System.out.println("\n== 无锁栈 ==");
        LockFreeStack<Integer> stack = new LockFreeStack<>();
        for (int i = 1; i <= 3; i++) stack.push(i);
        System.out.println("  pop 顺序: " + stack.pop() + " " + stack.pop() + " " + stack.pop() + "（ConcurrentLinkedQueue 也是类似的 CAS 链表）");
        System.out.println("  其他原子类：AtomicIntegerArray、AtomicIntegerFieldUpdater（省内存）、Java 9 VarHandle 替代 Unsafe");
    }

    static long time(Runnable op, int threads, int loops) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(threads);
        long start = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                for (int j = 0; j < loops; j++) op.run();
                done.countDown();
            }).start();
        }
        done.await();
        return System.nanoTime() - start;
    }
}
