package com.javalearning.patterns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 10-6 分布式：锁 / 事务 / 一致性（CAP）。
 *
 * <p>全部用内存对象模拟多节点：
 * 分布式锁（带租约 + fencing token 解决“锁过期后旧持有者继续写”）、
 * 2PC 两阶段提交（协调者宕机导致阻塞）、TCC 与 Saga 补偿、
 * 以及一个带网络分区开关的双节点副本，直观看到 CP 与 AP 的取舍。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.patterns.DistributedDemo
 */
public class DistributedDemo {

    public static void main(String[] args) throws Exception {
        distributedLock();
        twoPhaseCommit();
        tccAndSaga();
        capTradeoff();
        printSummary();
    }

    // ================= 分布式锁 =================
    /** 模拟 Redis/ZK 的锁服务：key -> (owner, 过期时间, fencing token)。 */
    static class LockService {
        record Lease(String owner, long expireAt, long token) { }
        private final Map<String, Lease> locks = new ConcurrentHashMap<>();
        private final AtomicLong tokenSeq = new AtomicLong();

        synchronized Lease tryLock(String key, String owner, long ttlMs) {
            Lease cur = locks.get(key);
            if (cur != null && cur.expireAt() > System.currentTimeMillis()) return null; // 被别人持有且未过期
            Lease lease = new Lease(owner, System.currentTimeMillis() + ttlMs, tokenSeq.incrementAndGet()); // 单调递增 token
            locks.put(key, lease);
            return lease;
        }

        synchronized void unlock(String key, String owner) {
            Lease cur = locks.get(key);
            if (cur != null && cur.owner().equals(owner)) locks.remove(key); // 只能解自己的锁
        }
    }

    /** 被保护的资源：只接受比上次更大的 fencing token，拒绝“僵尸写入”。 */
    static class Storage {
        private long lastToken = -1;
        final List<String> writes = new ArrayList<>();

        synchronized boolean write(long token, String data) {
            if (token <= lastToken) return false; // 过期锁持有者的 token 更小 -> 拒绝
            lastToken = token;
            writes.add(data);
            return true;
        }
    }

    static void distributedLock() throws Exception {
        System.out.println("== 分布式锁 ==");
        LockService locks = new LockService();
        AtomicInteger counter = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(8);
        for (int i = 0; i < 8; i++) {
            String node = "node-" + i;
            pool.submit(() -> {
                for (int j = 0; j < 100; j++) {
                    LockService.Lease l = locks.tryLock("inventory:A1", node, 1000);
                    if (l == null) { failed.incrementAndGet(); continue; }
                    try {
                        int v = counter.get();
                        counter.set(v + 1); // 非原子的读-改-写，靠锁保证
                    } finally {
                        locks.unlock("inventory:A1", node);
                    }
                }
                done.countDown();
            });
        }
        done.await();
        pool.shutdown();
        System.out.println("  8 节点 x 100 次：成功计数 " + counter.get() + "，竞争失败 " + failed.get() + "，无丢失更新");

        System.out.println("  锁过期问题 + fencing token：");
        Storage storage = new Storage();
        LockService.Lease a = locks.tryLock("file", "A", 50);
        System.out.println("      A 拿到锁 token=" + a.token() + "，然后发生 GC 停顿 / 网络延迟超过 TTL...");
        Thread.sleep(80);
        LockService.Lease b = locks.tryLock("file", "B", 1000);
        System.out.println("      锁过期，B 拿到锁 token=" + b.token() + "，B 写入 -> " + storage.write(b.token(), "B-data"));
        System.out.println("      A 醒来以为自己还持锁，写入 -> " + storage.write(a.token(), "A-data") + "（token 过旧被存储层拒绝）");
        System.out.println("      最终数据: " + storage.writes + "。没有 fencing token 时 A 会覆盖 B 的写入");
        System.out.println("  实现对比：Redis SET NX PX（AP，快，主从切换可能双持锁，Redisson 看门狗续期）| ZooKeeper 临时顺序节点（CP，会话断开自动释放，watch 前驱避免惊群）| etcd lease + revision 自带 fencing");
    }

    // ================= 2PC =================
    static class Participant {
        final String name;
        boolean prepared;
        boolean committed;
        boolean failOnPrepare;
        Participant(String name, boolean failOnPrepare) { this.name = name; this.failOnPrepare = failOnPrepare; }

        boolean prepare() {
            if (failOnPrepare) { System.out.println("      " + name + ": prepare 失败（如余额不足）"); return false; }
            prepared = true; // 写 undo/redo 日志、锁定资源，等待协调者指令
            System.out.println("      " + name + ": prepared（资源已锁定，等待决定）");
            return true;
        }
        void commit() { committed = true; prepared = false; System.out.println("      " + name + ": commit"); }
        void rollback() { prepared = false; System.out.println("      " + name + ": rollback"); }
    }

    static void twoPhaseCommit() {
        System.out.println("\n== 2PC 两阶段提交 ==");
        for (boolean failing : new boolean[]{false, true}) {
            List<Participant> ps = List.of(new Participant("订单库", false), new Participant("账户库", failing));
            System.out.println("  场景: 账户库 " + (failing ? "prepare 失败" : "正常"));
            System.out.println("    阶段 1 投票:");
            boolean all = ps.stream().allMatch(Participant::prepare); // 注意：allMatch 短路，失败后不会再问后面的
            System.out.println("    阶段 2 " + (all ? "全部同意 -> 全局 commit" : "有人否决 -> 全局 rollback"));
            ps.forEach(p -> { if (all) p.commit(); else if (p.prepared) p.rollback(); });
        }
        System.out.println("  问题：同步阻塞（prepared 期间资源一直锁着）、协调者单点（阶段 2 前宕机，参与者永远阻塞）、数据不一致（commit 消息部分丢失）");
        System.out.println("  3PC 加 CanCommit 阶段与超时机制缓解阻塞但仍不完美；XA 是 2PC 的数据库标准接口，Seata XA / AT 模式基于此（AT 自动生成反向 SQL 做回滚）");
    }

    // ================= TCC / Saga =================
    interface TccAction {
        String name();
        boolean tryReserve();
        void confirm();
        void cancel();
    }

    static TccAction tcc(String name, boolean tryOk) {
        return new TccAction() {
            public String name() { return name; }
            public boolean tryReserve() { System.out.println("      " + name + ".try " + (tryOk ? "预留成功（冻结额度）" : "失败")); return tryOk; }
            public void confirm() { System.out.println("      " + name + ".confirm 扣减冻结"); }
            public void cancel() { System.out.println("      " + name + ".cancel 释放冻结"); }
        };
    }

    static void tccAndSaga() {
        System.out.println("\n== TCC（Try-Confirm-Cancel） ==");
        List<TccAction> actions = List.of(tcc("库存", true), tcc("积分", true), tcc("余额", false));
        List<TccAction> reserved = new ArrayList<>();
        boolean ok = true;
        for (TccAction a : actions) {
            if (a.tryReserve()) reserved.add(a); else { ok = false; break; }
        }
        if (ok) reserved.forEach(TccAction::confirm); else { System.out.println("    余额 try 失败 -> 逆序 cancel 已预留的:"); for (int i = reserved.size() - 1; i >= 0; i--) reserved.get(i).cancel(); }
        System.out.println("  TCC 不锁数据库资源（业务层“冻结”），性能好但侵入性强：每个操作要写三个方法，且要处理空回滚、悬挂、幂等");

        System.out.println("\n== Saga：一串本地事务 + 对应补偿 ==");
        record Step(String name, Runnable action, Runnable compensate) { }
        AtomicInteger stock = new AtomicInteger(10);
        AtomicInteger points = new AtomicInteger(0);
        List<Step> steps = List.of(
                new Step("创建订单", () -> System.out.println("      T1 创建订单"), () -> System.out.println("      C1 取消订单")),
                new Step("扣库存", () -> { stock.decrementAndGet(); System.out.println("      T2 扣库存 -> " + stock); }, () -> { stock.incrementAndGet(); System.out.println("      C2 回补库存 -> " + stock); }),
                new Step("加积分", () -> { points.addAndGet(10); System.out.println("      T3 加积分 -> " + points); }, () -> { points.addAndGet(-10); System.out.println("      C3 扣回积分 -> " + points); }),
                new Step("扣款", () -> { throw new IllegalStateException("银行接口超时"); }, () -> { }));
        List<Step> doneSteps = new ArrayList<>();
        try {
            for (Step s : steps) { s.action().run(); doneSteps.add(s); }
        } catch (RuntimeException e) {
            System.out.println("    扣款失败: " + e.getMessage() + " -> 逆序执行补偿:");
            for (int i = doneSteps.size() - 1; i >= 0; i--) doneSteps.get(i).compensate().run();
        }
        System.out.println("  最终 stock=" + stock + " points=" + points + "（回到初始值，最终一致）");
        System.out.println("  Saga 每步立即提交（无隔离性，中间状态可见），适合长流程跨服务；编排式（中央协调器 Seata Saga / Temporal）vs 协同式（各服务监听事件接力）");
        System.out.println("  更常用的简化版：本地消息表 / 事务消息（RocketMQ 半消息）+ 消费端幂等 => 可靠事件最终一致");
    }

    // ================= CAP =================
    /** 两个副本节点，中间有一条可以被“切断”的网络。 */
    static class Replica {
        final String name;
        final Map<String, String> data = new HashMap<>();
        Replica(String name) { this.name = name; }
    }

    static class ReplicatedStore {
        final Replica a = new Replica("A");
        final Replica b = new Replica("B");
        boolean partitioned = false;
        final boolean preferConsistency;

        ReplicatedStore(boolean preferConsistency) { this.preferConsistency = preferConsistency; }

        String write(Replica target, String k, String v) {
            Replica other = target == a ? b : a;
            if (partitioned) {
                if (preferConsistency) return "写入被拒绝（无法同步到多数派，牺牲可用性 -> CP）";
                target.data.put(k, v);
                return "本地写入成功，稍后补同步（返回可能过期的数据 -> AP）";
            }
            target.data.put(k, v);
            other.data.put(k, v); // 同步复制
            return "写入并同步成功";
        }

        String read(Replica r, String k) {
            if (partitioned && preferConsistency) return "读取被拒绝（不能确认是最新值）";
            return r.data.getOrDefault(k, "<null>");
        }
    }

    static void capTradeoff() {
        System.out.println("\n== CAP：分区发生时，在一致性与可用性之间二选一 ==");
        for (boolean cp : new boolean[]{true, false}) {
            ReplicatedStore s = new ReplicatedStore(cp);
            System.out.println("  " + (cp ? "CP 系统（ZooKeeper / etcd / HBase）" : "AP 系统（Eureka / Cassandra / DynamoDB）"));
            System.out.println("    正常: 写 A -> " + s.write(s.a, "x", "1") + "，读 B = " + s.read(s.b, "x"));
            s.partitioned = true;
            System.out.println("    分区: 写 A -> " + s.write(s.a, "x", "2"));
            System.out.println("    分区: 读 B = " + s.read(s.b, "x") + (cp ? "" : "  <- 读到旧值 1，分区恢复后再收敛"));
        }
        System.out.println("  P 不是选项：网络一定会分区。所以实际是 “分区时选 C 还是 A”，且同一系统不同操作可以不同选择（Nacos 支持 AP/CP 切换）");
        System.out.println("  BASE：基本可用、软状态、最终一致 —— 互联网系统对 AP 的务实表述");
        System.out.println("  一致性光谱：线性一致 > 顺序一致 > 因果一致 > 最终一致；共识算法 Paxos / Raft（选主 + 日志复制 + 多数派）是 CP 的基石（etcd、Nacos CP 模式、Kafka KRaft）");
    }

    static void printSummary() {
        System.out.println("""

                分布式事务选型：
                  强一致、短事务、单体内多库  -> XA / Seata AT（简单，但锁与性能代价大）
                  高性能、核心资金类           -> TCC（侵入强，需处理空回滚/悬挂/幂等）
                  跨多服务长流程               -> Saga（无隔离，需设计补偿）
                  绝大多数业务                 -> 可靠事件 / 事务消息 + 幂等消费（最终一致，最常用）
                  先问：真的需要分布式事务吗？能否把需要一致的数据放进同一个聚合/同一个库？
                幂等是分布式系统的基本功：唯一请求 ID + 去重表、数据库唯一键、乐观锁版本号、状态机前置校验
                分布式 ID、分布式锁、分布式事务、分布式缓存、分布式会话 —— 每一个都是在“没有全局时钟和共享内存”的前提下重新发明单机原语
                参考：《数据密集型应用系统设计》（DDIA）第 5/7/8/9 章、seata/seata、apache/zookeeper、etcd-io/etcd、Martin Kleppmann "How to do distributed locking"
                """);
    }
}
