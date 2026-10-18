package com.javalearning.concurrency;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 知识点 3-9：ThreadLocal。
 *
 * <p>重点：每个线程独立副本的实现（Thread.threadLocals → ThreadLocalMap，key 为弱引用）、
 * 典型用途（线程不安全对象复用、请求上下文传递）、线程池场景下必须 remove 否则内存泄漏与脏数据、
 * InheritableThreadLocal 与子线程、父子线程池传递需要 TransmittableThreadLocal。
 */
public class ThreadLocalDemo {

    /** SimpleDateFormat 线程不安全，用 ThreadLocal 给每个线程一份。 */
    static final ThreadLocal<SimpleDateFormat> FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    /** 模拟 Web 框架中的"当前登录用户"上下文。 */
    static final class UserContext {
        private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

        static void set(String user) { CURRENT.set(user); }
        static String get() { return CURRENT.get(); }
        static void clear() { CURRENT.remove(); }
    }

    static void serviceLayer() {
        // 不需要把 user 作为参数层层传递
        System.out.println("    service 层读取当前用户: " + UserContext.get() + "（线程 " + Thread.currentThread().getName() + "）");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 线程隔离的 SimpleDateFormat ==");
        Thread t1 = new Thread(() -> System.out.println("  线程1 格式化: " + FORMAT.get().format(new Date()) + "，实例 " + System.identityHashCode(FORMAT.get())));
        Thread t2 = new Thread(() -> System.out.println("  线程2 格式化: " + FORMAT.get().format(new Date()) + "，实例 " + System.identityHashCode(FORMAT.get())));
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("  两个线程拿到不同实例；Java 8 后更推荐线程安全的 DateTimeFormatter");

        System.out.println("\n== 请求上下文 + 线程池泄漏演示 ==");
        ExecutorService pool = Executors.newFixedThreadPool(1); // 单线程池，线程会被复用
        pool.submit(() -> {
            UserContext.set("alice");
            serviceLayer();
            // 忘记 clear()
        }).get();
        pool.submit(() -> {
            System.out.println("  第二个请求没有设置用户，却读到了上一个请求残留的: " + UserContext.get() + "  <- 脏数据");
            UserContext.clear();
        }).get();
        pool.submit(() -> {
            try {
                UserContext.set("bob");
                serviceLayer();
            } finally {
                UserContext.clear(); // 正确姿势：finally 中 remove
            }
        }).get();
        pool.submit(() -> System.out.println("  正确清理后下一个请求读到: " + UserContext.get())).get();
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.SECONDS);

        System.out.println("\n== InheritableThreadLocal ==");
        InheritableThreadLocal<String> inheritable = new InheritableThreadLocal<>();
        ThreadLocal<String> plain = new ThreadLocal<>();
        inheritable.set("父线程的值");
        plain.set("父线程的值");
        Thread child = new Thread(() -> System.out.println("  子线程读取 inheritable = " + inheritable.get() + "，plain = " + plain.get()));
        child.start();
        child.join();
        System.out.println("  InheritableThreadLocal 在 new Thread 时拷贝父线程值；但线程池里的线程早已创建，传递会失效 -> 用阿里 TransmittableThreadLocal");

        System.out.println("""

                内存结构：Thread.threadLocals -> ThreadLocalMap(Entry[])，Entry 继承 WeakReference<ThreadLocal>
                  key（ThreadLocal）是弱引用：ThreadLocal 对象没有强引用时可被回收
                  value 是强引用：线程存活（线程池）且不 remove，value 永远无法回收 -> 内存泄漏
                  set/get 时会顺带清理 key 为 null 的 Entry，但不保证及时；养成 try/finally remove 的习惯
                """);
    }
}
