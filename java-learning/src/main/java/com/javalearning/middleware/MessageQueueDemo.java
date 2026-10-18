package com.javalearning.middleware;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 8-4 消息队列：Kafka / RocketMQ / RabbitMQ。
 *
 * <p>手写一个 Kafka 风格的 MiniBroker：Topic -> 多个 Partition（追加日志）-> 消费组按 offset 拉取。
 * 演示：分区有序、消费组负载均衡（同组分摊，不同组各收一份）、offset 提交语义导致的重复消费、
 * 幂等消费者、以及延迟消息（RocketMQ 特性）。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.middleware.MessageQueueDemo
 */
public class MessageQueueDemo {

    record Message(String key, String body, long timestamp) { }

    /** 一个分区就是一个只追加的日志，offset = 下标。Kafka 的高吞吐核心：顺序写磁盘 + 页缓存 + 零拷贝。 */
    static class Partition {
        private final List<Message> log = new CopyOnWriteArrayList<>();
        long append(Message m) { log.add(m); return log.size() - 1; }
        List<Message> poll(long fromOffset, int max) {
            if (fromOffset >= log.size()) return List.of();
            return new ArrayList<>(log.subList((int) fromOffset, (int) Math.min(log.size(), fromOffset + max)));
        }
        long endOffset() { return log.size(); }
    }

    static class MiniBroker {
        private final Map<String, Partition[]> topics = new ConcurrentHashMap<>();
        /** 消费组 offset：group -> topic-partition -> 已提交 offset。 */
        private final Map<String, Map<String, Long>> committed = new ConcurrentHashMap<>();

        void createTopic(String topic, int partitions) {
            Partition[] ps = new Partition[partitions];
            for (int i = 0; i < partitions; i++) ps[i] = new Partition();
            topics.put(topic, ps);
        }

        /** 生产者按 key 哈希选分区：同 key -> 同分区 -> 有序。无 key 则轮询。 */
        int send(String topic, Message m) {
            Partition[] ps = topics.get(topic);
            int p = m.key() == null ? (int) (m.timestamp() % ps.length) : Math.floorMod(m.key().hashCode(), ps.length);
            ps[p].append(m);
            return p;
        }

        List<Message> poll(String group, String topic, int partition, int max) {
            long offset = committed.computeIfAbsent(group, g -> new ConcurrentHashMap<>()).getOrDefault(topic + "-" + partition, 0L);
            return topics.get(topic)[partition].poll(offset, max);
        }

        void commit(String group, String topic, int partition, long offset) {
            committed.get(group).put(topic + "-" + partition, offset);
        }

        long committed(String group, String topic, int partition) {
            return committed.getOrDefault(group, Map.of()).getOrDefault(topic + "-" + partition, 0L);
        }

        int partitions(String topic) { return topics.get(topic).length; }
    }

    public static void main(String[] args) throws Exception {
        MiniBroker broker = new MiniBroker();
        broker.createTopic("orders", 3);

        partitionOrdering(broker);
        consumerGroups(broker);
        atLeastOnceAndIdempotent(broker);
        delayedMessage();
        printComparison();
    }

    static void partitionOrdering(MiniBroker broker) {
        System.out.println("== 分区与有序性 ==");
        String[] users = {"u1", "u2", "u3", "u4"};
        for (int i = 1; i <= 8; i++) {
            String key = users[i % users.length];
            int p = broker.send("orders", new Message(key, "order-" + i + " by " + key, i));
            System.out.printf("  key=%s -> partition %d%n", key, p);
        }
        System.out.println("  同一用户的消息始终进同一分区，分区内严格有序；全局有序只能单分区（牺牲并行度）");
    }

    static void consumerGroups(MiniBroker broker) {
        System.out.println("\n== 消费组：同组分摊分区，不同组各自消费全量 ==");
        // 组 A 两个消费者，分区分配：c1 -> p0,p1；c2 -> p2（真实由协调者 Rebalance 决定）
        int total = 0;
        for (int p = 0; p < broker.partitions("orders"); p++) {
            String consumer = p < 2 ? "A-c1" : "A-c2";
            List<Message> msgs = broker.poll("groupA", "orders", p, 100);
            total += msgs.size();
            System.out.printf("  [%s] 分区%d 拉到 %d 条: %s%n", consumer, p, msgs.size(), msgs.stream().map(Message::body).toList());
            broker.commit("groupA", "orders", p, msgs.size());
        }
        int totalB = 0;
        for (int p = 0; p < broker.partitions("orders"); p++) {
            totalB += broker.poll("groupB", "orders", p, 100).size();
        }
        System.out.println("  groupA 共消费 " + total + " 条（分摊），groupB 独立消费 " + totalB + " 条（广播到另一业务）");
        System.out.println("  一个分区同一时刻只能被组内一个消费者消费 -> 消费者数 > 分区数时多余的消费者空闲");
    }

    static void atLeastOnceAndIdempotent(MiniBroker broker) throws Exception {
        System.out.println("\n== 至少一次语义 + 幂等消费 ==");
        broker.createTopic("payments", 1);
        for (int i = 1; i <= 5; i++) {
            broker.send("payments", new Message("pay-" + i, "pay-" + i, i));
        }
        Set<String> processedIds = ConcurrentHashMap.newKeySet(); // 幂等表（生产用 DB 唯一键 / Redis SETNX）
        AtomicInteger handled = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();

        // 第一次消费：处理完 5 条但在提交 offset 之前"宕机"
        List<Message> batch = broker.poll("pay-group", "payments", 0, 10);
        for (Message m : batch) {
            if (processedIds.add(m.key())) handled.incrementAndGet(); else duplicates.incrementAndGet();
        }
        System.out.println("  第一次拉取处理 " + batch.size() + " 条，提交 offset 前消费者崩溃（offset 仍为 " + broker.committed("pay-group", "payments", 0) + "）");

        // 重启后重新拉取 -> 消息重复投递
        batch = broker.poll("pay-group", "payments", 0, 10);
        for (Message m : batch) {
            if (processedIds.add(m.key())) handled.incrementAndGet(); else duplicates.incrementAndGet();
        }
        broker.commit("pay-group", "payments", 0, batch.size());
        System.out.println("  重启后再次拉到 " + batch.size() + " 条，幂等表拦截重复 " + duplicates.get() + " 条，实际业务处理 " + handled.get() + " 次");
        System.out.println("  结论：MQ 保证至少一次，精确一次要靠业务幂等（唯一业务 ID + 去重表 / 乐观锁版本号 / 状态机）");
    }

    static void delayedMessage() throws Exception {
        System.out.println("\n== 延迟消息（订单 30 分钟未支付自动关闭的缩小版） ==");
        var scheduler = Executors.newScheduledThreadPool(1);
        CountDownLatch latch = new CountDownLatch(2);
        long t0 = System.currentTimeMillis();
        for (int delay : new int[]{300, 100}) {
            scheduler.schedule(() -> {
                System.out.printf("  %4d ms 后收到延迟消息(delay=%d)%n", System.currentTimeMillis() - t0, delay);
                latch.countDown();
            }, delay, TimeUnit.MILLISECONDS);
        }
        latch.await();
        scheduler.shutdown();
        System.out.println("  RocketMQ 延迟级别 1s~2h（5.x 支持任意时间）；RabbitMQ 用 TTL + 死信队列或延迟插件；Kafka 原生不支持需自建时间轮");
    }

    static void printComparison() {
        System.out.println("""

                为什么用 MQ：异步（缩短响应）、解耦（生产者不关心谁消费）、削峰（突发流量进队列慢慢消费）
                代价：系统复杂度↑、一致性问题（消息丢失/重复/乱序）、可用性依赖 MQ

                三大 MQ 对比：
                  Kafka     吞吐百万级，分区日志 + 顺序写 + 零拷贝；日志收集、流处理、大数据；功能较少（无延迟消息/死信）
                  RocketMQ  阿里，Java 编写，十万级吞吐；事务消息、延迟消息、顺序消息、消息轨迹、死信队列；电商业务首选
                  RabbitMQ  Erlang，AMQP 协议，Exchange 路由灵活（direct/fanout/topic/headers）；延迟低，吞吐万级；中小业务

                可靠性三段：
                  生产端不丢：同步发送 + 重试 / Kafka acks=all + 幂等生产者；RocketMQ 事务消息（半消息 + 回查）解决本地事务与发消息的原子性
                  Broker 不丢：刷盘策略（同步刷盘 vs 异步）、多副本（Kafka ISR min.insync.replicas=2）
                  消费端不丢：手动 ACK，处理成功再提交 offset；失败重试 N 次进死信队列人工处理
                顺序消费：同 key 发同分区 + 单线程消费该分区（或按 key 再分发到内存队列）
                消息堆积：扩分区 + 扩消费者；临时建新 Topic 转储；修复消费逻辑后快速消费
                参考：apache/kafka、apache/rocketmq、rabbitmq/rabbitmq-server、spring-cloud/spring-cloud-stream
                """);
    }
}
