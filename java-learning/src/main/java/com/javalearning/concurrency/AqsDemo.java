package com.javalearning.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 知识点 3-5：AQS（AbstractQueuedSynchronizer）原理。
 *
 * <p>重点：AQS = volatile int state + CLH 双向等待队列 + 模板方法；
 * 独占模式（ReentrantLock）重写 tryAcquire/tryRelease，共享模式（Semaphore、CountDownLatch）重写 tryAcquireShared/tryReleaseShared。
 * 本例用不到 40 行各实现一个不可重入互斥锁与一个闭锁，直观展示 JUC 的构建方式。
 */
public class AqsDemo {

    /** 独占模式：state 0 = 未锁定，1 = 已锁定。 */
    static class SimpleMutex {
        private final Sync sync = new Sync();

        private static class Sync extends AbstractQueuedSynchronizer {
            @Override
            protected boolean tryAcquire(int arg) {
                if (compareAndSetState(0, 1)) {       // CAS 抢锁
                    setExclusiveOwnerThread(Thread.currentThread());
                    return true;
                }
                return false;                           // 失败则由 AQS 把线程包装为 Node 入队并 park
            }

            @Override
            protected boolean tryRelease(int arg) {
                if (getState() == 0) throw new IllegalMonitorStateException();
                setExclusiveOwnerThread(null);
                setState(0);                            // volatile 写，释放后 AQS 会 unpark 后继节点
                return true;
            }

            @Override
            protected boolean isHeldExclusively() {
                return getState() == 1 && getExclusiveOwnerThread() == Thread.currentThread();
            }
        }

        void lock() { sync.acquire(1); }
        void unlock() { sync.release(1); }
        boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }
        int queueLength() { return sync.getQueueLength(); }
    }

    /** 共享模式：state 为剩余计数，归零后所有等待线程被一起唤醒。 */
    static class SimpleLatch {
        private final Sync sync;

        SimpleLatch(int count) {
            sync = new Sync(count);
        }

        private static class Sync extends AbstractQueuedSynchronizer {
            Sync(int count) { setState(count); }

            @Override
            protected int tryAcquireShared(int arg) {
                return getState() == 0 ? 1 : -1;        // >= 0 表示获取成功
            }

            @Override
            protected boolean tryReleaseShared(int arg) {
                for (;;) {
                    int c = getState();
                    if (c == 0) return false;
                    if (compareAndSetState(c, c - 1)) return c - 1 == 0; // 归零时返回 true 触发传播唤醒
                }
            }
        }

        void countDown() { sync.releaseShared(1); }
        void await() throws InterruptedException { sync.acquireSharedInterruptibly(1); }
    }

    static int shared = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("== 用 AQS 实现互斥锁 ==");
        SimpleMutex mutex = new SimpleMutex();
        int threads = 8, loops = 5000;
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                for (int j = 0; j < loops; j++) {
                    mutex.lock();
                    try {
                        shared++;
                    } finally {
                        mutex.unlock();
                    }
                }
                done.countDown();
            }).start();
        }
        done.await();
        System.out.println("  8 线程累加结果 = " + shared + "（期望 " + threads * loops + "）");

        mutex.lock();
        Thread waiter = new Thread(() -> {
            try {
                System.out.println("  等待线程 tryLock 结果 = " + mutex.tryLock(100, TimeUnit.MILLISECONDS) + "（超时）");
            } catch (InterruptedException ignored) { }
        });
        waiter.start();
        Thread.sleep(30);
        System.out.println("  主线程持锁期间 CLH 队列长度 = " + mutex.queueLength());
        waiter.join();
        mutex.unlock();

        System.out.println("\n== 用 AQS 实现闭锁 ==");
        SimpleLatch latch = new SimpleLatch(3);
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            new Thread(() -> {
                try { Thread.sleep(id * 50L); } catch (InterruptedException ignored) { }
                System.out.println("  子任务 " + id + " 完成");
                latch.countDown();
            }).start();
        }
        latch.await();
        System.out.println("  所有子任务完成，主线程继续");
        System.out.println("""

                AQS 核心：
                  - volatile int state：资源状态，由子类赋予语义（锁次数 / 许可数 / 计数）
                  - CLH 变体双向队列：获取失败的线程封装为 Node，LockSupport.park 挂起；释放时 unpark 后继
                  - 模板方法：acquire/release 流程固定，子类只实现 tryAcquire/tryRelease（独占）或 tryAcquireShared/tryReleaseShared（共享）
                  - ConditionObject：条件队列，await 时从同步队列转移到条件队列，signal 时转移回来
                基于 AQS 的类：ReentrantLock、ReentrantReadWriteLock、Semaphore、CountDownLatch、ThreadPoolExecutor.Worker、FutureTask(早期)
                """);
    }
}
