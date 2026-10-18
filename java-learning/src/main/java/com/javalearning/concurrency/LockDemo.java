package com.javalearning.concurrency;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/**
 * 知识点 3-4：JUC 锁 —— ReentrantLock / ReentrantReadWriteLock / StampedLock。
 *
 * <p>重点：Lock 相比 synchronized 的增强（可中断、可超时、公平锁、多个 Condition）、
 * 标准写法 lock() 在 try 外 / unlock() 在 finally、读写锁提升读多写少性能、StampedLock 乐观读。
 */
public class LockDemo {

    /** 用 ReentrantLock + 两个 Condition 实现有界缓冲区（ArrayBlockingQueue 的原理）。 */
    static class BoundedBuffer<T> {
        private final Object[] items;
        private int head, tail, count;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        BoundedBuffer(int capacity) {
            items = new Object[capacity];
        }

        void put(T item) throws InterruptedException {
            lock.lock();
            try {
                while (count == items.length) notFull.await();
                items[tail] = item;
                tail = (tail + 1) % items.length;
                count++;
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        T take() throws InterruptedException {
            lock.lock();
            try {
                while (count == 0) notEmpty.await();
                T item = (T) items[head];
                items[head] = null;
                head = (head + 1) % items.length;
                count--;
                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }
    }

    /** 读写锁保护的缓存：读读并发、读写互斥、写写互斥。 */
    static class ReadWriteCache {
        private final Map<String, String> map = new HashMap<>();
        private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();

        String get(String key) {
            rw.readLock().lock();
            try {
                return map.get(key);
            } finally {
                rw.readLock().unlock();
            }
        }

        void put(String key, String value) {
            rw.writeLock().lock();
            try {
                map.put(key, value);
            } finally {
                rw.writeLock().unlock();
            }
        }
    }

    /** StampedLock 乐观读：读时不加锁，读完校验版本戳。 */
    static class Point {
        private double x, y;
        private final StampedLock sl = new StampedLock();

        void move(double dx, double dy) {
            long stamp = sl.writeLock();
            try {
                x += dx;
                y += dy;
            } finally {
                sl.unlockWrite(stamp);
            }
        }

        double distanceFromOrigin() {
            long stamp = sl.tryOptimisticRead();
            double cx = x, cy = y;
            if (!sl.validate(stamp)) {           // 期间有写入，退化为悲观读锁
                stamp = sl.readLock();
                try {
                    cx = x;
                    cy = y;
                } finally {
                    sl.unlockRead(stamp);
                }
            }
            return Math.hypot(cx, cy);
        }
    }

    public static void main(String[] args) throws Exception {
        tryLockAndInterrupt();
        boundedBuffer();
        readWriteAndStamped();
    }

    static void tryLockAndInterrupt() throws InterruptedException {
        System.out.println("== tryLock / lockInterruptibly ==");
        ReentrantLock lock = new ReentrantLock(true); // 公平锁：按等待顺序获取，吞吐略低
        lock.lock();
        Thread t = new Thread(() -> {
            try {
                if (lock.tryLock(200, TimeUnit.MILLISECONDS)) {
                    try {
                        System.out.println("  不应拿到锁");
                    } finally {
                        lock.unlock();
                    }
                } else {
                    System.out.println("  tryLock 等待 200ms 超时放弃（synchronized 做不到）");
                }
            } catch (InterruptedException e) {
                System.out.println("  等待锁时被中断");
            }
        });
        t.start();
        t.join();
        System.out.println("  isFair=" + lock.isFair() + " holdCount=" + lock.getHoldCount() + " isLocked=" + lock.isLocked());
        lock.unlock();
    }

    static void boundedBuffer() throws InterruptedException {
        System.out.println("\n== Condition 实现生产者-消费者 ==");
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(2);
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.put(i);
                    System.out.println("  生产 " + i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    Thread.sleep(50);
                    System.out.println("  消费 " + buffer.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println("  多个 Condition 可以精确唤醒（notFull / notEmpty），synchronized 只有一个等待队列");
    }

    static void readWriteAndStamped() {
        System.out.println("\n== 读写锁与 StampedLock ==");
        ReadWriteCache cache = new ReadWriteCache();
        cache.put("k", "v");
        System.out.println("  读写锁 get(k) = " + cache.get("k") + "；读锁可被多个线程同时持有，写锁独占，写锁可降级为读锁但读锁不能升级");
        Point p = new Point();
        p.move(3, 4);
        System.out.println("  StampedLock 乐观读距离 = " + p.distanceFromOrigin() + "；它不可重入、不支持 Condition，但读性能最好");
    }
}
