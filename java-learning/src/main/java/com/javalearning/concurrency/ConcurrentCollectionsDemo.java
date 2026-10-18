package com.javalearning.concurrency;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * 知识点 3-7：并发容器与阻塞队列。
 *
 * <p>重点：ConcurrentHashMap 的原子复合操作、CopyOnWriteArrayList 的适用边界、ConcurrentSkipListMap 的有序并发、
 * BlockingQueue 家族的差异（有界/无界、公平性、DelayQueue 做延迟任务、SynchronousQueue 做直接交接）。
 */
public class ConcurrentCollectionsDemo {

    record DelayedTask(String name, long triggerAt) implements Delayed {
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(triggerAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(triggerAt, ((DelayedTask) o).triggerAt);
        }
    }

    public static void main(String[] args) throws Exception {
        concurrentHashMapIdioms();
        copyOnWrite();
        skipList();
        blockingQueues();
        delayQueue();
    }

    static void concurrentHashMapIdioms() {
        System.out.println("== ConcurrentHashMap 原子复合操作 ==");
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        // 错误：先 get 再 put 不是原子的（check-then-act 竞态）
        // if (!map.containsKey(k)) map.put(k, 1);
        map.putIfAbsent("a", 1);
        map.merge("a", 1, Integer::sum);
        map.compute("b", (k, v) -> v == null ? 10 : v + 10);
        map.computeIfAbsent("c", k -> k.length());
        System.out.println("  putIfAbsent / merge / compute / computeIfAbsent 都是原子的: " + map);
        System.out.println("  迭代器弱一致：不会抛 ConcurrentModificationException，但可能看不到迭代开始后的修改");
    }

    static void copyOnWrite() {
        System.out.println("\n== CopyOnWriteArrayList ==");
        CopyOnWriteArrayList<String> listeners = new CopyOnWriteArrayList<>(List.of("L1", "L2"));
        for (String l : listeners) {
            if (l.equals("L1")) listeners.add("L3"); // 迭代期间安全添加
        }
        System.out.println("  遍历中添加不报错，最终 " + listeners + "；每次写复制整个数组，适合监听器列表 / 白名单等读多写少场景");
    }

    static void skipList() {
        System.out.println("\n== ConcurrentSkipListMap ==");
        ConcurrentSkipListMap<Integer, String> ranking = new ConcurrentSkipListMap<>();
        ranking.put(90, "Alice");
        ranking.put(75, "Bob");
        ranking.put(88, "Cathy");
        System.out.println("  并发有序 Map（跳表，O(log n)）: " + ranking + "，>= 80 的: " + ranking.tailMap(80));
        System.out.println("  跳表比并发红黑树更容易用 CAS 实现，这就是 JUC 没有 ConcurrentTreeMap 的原因");
    }

    static void blockingQueues() throws InterruptedException {
        System.out.println("\n== BlockingQueue 家族 ==");
        BlockingQueue<Integer> bounded = new ArrayBlockingQueue<>(2);
        bounded.put(1);
        bounded.put(2);
        System.out.println("  ArrayBlockingQueue(2) 已满，offer 立即返回 " + bounded.offer(3)
                + "，offer 等 100ms 返回 " + bounded.offer(3, 100, TimeUnit.MILLISECONDS) + "，put 会一直阻塞");

        BlockingQueue<Integer> linked = new LinkedBlockingQueue<>(); // 默认 Integer.MAX_VALUE，相当于无界
        linked.offer(1);
        System.out.println("  LinkedBlockingQueue 两把锁（put/take 分离）吞吐更高，但默认无界 -> 线程池用它要指定容量");

        SynchronousQueue<String> handoff = new SynchronousQueue<>();
        Thread consumer = new Thread(() -> {
            try {
                System.out.println("  SynchronousQueue 消费者收到: " + handoff.take());
            } catch (InterruptedException ignored) { }
        });
        consumer.start();
        handoff.put("hello"); // 没有消费者就会一直阻塞
        consumer.join();
        System.out.println("  SynchronousQueue 容量为 0，生产者必须等消费者来接——newCachedThreadPool 用它直接交接任务");

        ConcurrentLinkedQueue<Integer> lockFree = new ConcurrentLinkedQueue<>(List.of(1, 2));
        System.out.println("  ConcurrentLinkedQueue 是非阻塞（CAS）无界队列，poll 空时返回 null: " + lockFree.poll() + lockFree.poll() + lockFree.poll());
    }

    static void delayQueue() throws InterruptedException {
        System.out.println("\n== DelayQueue 延迟任务 ==");
        DelayQueue<DelayedTask> queue = new DelayQueue<>();
        long now = System.currentTimeMillis();
        queue.put(new DelayedTask("订单超时关闭", now + 300));
        queue.put(new DelayedTask("发送提醒", now + 100));
        while (!queue.isEmpty()) {
            DelayedTask task = queue.take(); // 只有到期的元素才能取出
            System.out.printf("  %4d ms 后执行: %s%n", System.currentTimeMillis() - now, task.name());
        }
        System.out.println("  DelayQueue = PriorityQueue + 到期才可取，是 ScheduledThreadPoolExecutor 与本地延迟任务的基础");
    }
}
