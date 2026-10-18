package com.javalearning.jvm;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 知识点 4-7：性能调优工具 —— jps / jstack / jmap / jstat / Arthas。
 *
 * <p>重点：命令行工具的用途与常用参数、用 ThreadMXBean 以编程方式实现 jstack 的死锁检测、
 * 排查 CPU 飙高（top -Hp → printf %x → jstack 搜线程 id）的标准流程、Arthas 常用命令。
 * 运行后程序会保持 10 秒，可用 jps / jstack / jmap 实际练习。
 */
public class TuningToolsDemo {

    static final ReentrantLock LOCK_A = new ReentrantLock();
    static final ReentrantLock LOCK_B = new ReentrantLock();

    public static void main(String[] args) throws Exception {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        System.out.println("== 进程信息 ==");
        System.out.println("  PID = " + runtime.getPid() + "（等价 jps -l），启动参数 = " + runtime.getInputArguments());
        System.out.println("  JVM = " + runtime.getVmName() + " " + runtime.getVmVersion());

        System.out.println("\n== 制造一个死锁并用 ThreadMXBean 检测（jstack 的原理） ==");
        Thread t1 = new Thread(() -> deadlock(LOCK_A, LOCK_B), "deadlock-1");
        Thread t2 = new Thread(() -> deadlock(LOCK_B, LOCK_A), "deadlock-2");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        Thread.sleep(300);

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long[] deadlocked = threads.findDeadlockedThreads();
        if (deadlocked != null) {
            System.out.println("  检测到 " + deadlocked.length + " 个死锁线程：");
            for (ThreadInfo info : threads.getThreadInfo(deadlocked, true, true)) {
                System.out.printf("    \"%s\" 状态 %s，等待锁 %s，被线程 \"%s\" 持有%n",
                        info.getThreadName(), info.getThreadState(), info.getLockName(), info.getLockOwnerName());
            }
        }
        System.out.println("  jstack <pid> 输出末尾会直接打印 \"Found one Java-level deadlock\"");

        System.out.println("\n== CPU 占用 Top 线程（jstack 排查 CPU 飙高的编程版） ==");
        Thread busy = new Thread(() -> {
            long x = 0;
            while (!Thread.currentThread().isInterrupted()) x += x ^ System.nanoTime();
        }, "cpu-burner");
        busy.setDaemon(true);
        busy.start();
        Thread.sleep(500);
        long topCpu = -1;
        String topName = "";
        for (long id : threads.getAllThreadIds()) {
            long cpu = threads.getThreadCpuTime(id);
            ThreadInfo info = threads.getThreadInfo(id);
            if (info != null && cpu > topCpu) {
                topCpu = cpu;
                topName = info.getThreadName();
            }
        }
        System.out.printf("  CPU 时间最高的线程: %s（%d ms）%n", topName, TimeUnit.NANOSECONDS.toMillis(topCpu));
        System.out.println("  线上流程: top -Hp <pid> 找到线程号 -> printf '%x' 转十六进制 -> jstack <pid> | grep -A 20 nid=0x...");
        busy.interrupt();

        System.out.println("""

                常用命令：
                  jps -l                      列出 Java 进程
                  jstat -gcutil <pid> 1000    每秒打印各代使用率与 GC 次数/耗时（看 FGC 是否持续增长）
                  jstack <pid>                线程快照：死锁、CPU 飙高、线程池耗尽、阻塞在哪
                  jmap -histo:live <pid>      存活对象直方图（先怀疑排前面的大对象）
                  jmap -dump:live,format=b,file=heap.hprof <pid>   堆转储，用 MAT / VisualVM / JProfiler 分析支配树与泄漏路径
                  jcmd <pid> VM.flags / GC.heap_info / Thread.print   jcmd 是上述工具的统一入口
                  jinfo -flags <pid>          查看/动态修改部分 JVM 参数
                Arthas（阿里开源，attach 即用，无需重启）：
                  dashboard 全局面板 | thread -n 3 最忙线程 | thread -b 找阻塞 | jad 反编译 | watch 观察方法入参返回值
                  trace 方法内部调用耗时 | stack 调用来源 | profiler start/stop 生成火焰图 | redefine 热替换 class | ognl 执行表达式
                JFR + JMC：-XX:StartFlightRecording=duration=60s,filename=app.jfr，生产环境低开销（<2%）的事件级分析
                """);
        System.out.println("程序将保持运行 10 秒，可在另一个终端执行: jstack " + runtime.getPid() + " 或 jmap -histo " + runtime.getPid());
        Thread.sleep(10_000);
    }

    static void deadlock(ReentrantLock first, ReentrantLock second) {
        first.lock();
        try {
            Thread.sleep(100);
            second.lock(); // 另一线程以相反顺序加锁 -> 循环等待
            try {
                System.out.println("不会执行到这里");
            } finally {
                second.unlock();
            }
        } catch (InterruptedException ignored) {
        } finally {
            first.unlock();
        }
    }
}
