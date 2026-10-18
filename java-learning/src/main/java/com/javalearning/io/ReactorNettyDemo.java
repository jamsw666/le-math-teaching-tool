package com.javalearning.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 知识点 5-6：Reactor 模型与 Netty。
 *
 * <p>重点：Reactor 三种形态（单 Reactor 单线程 / 单 Reactor 多线程 / 主从 Reactor），
 * 本例用纯 NIO 实现"主从 Reactor"：Boss 线程只管 accept，把连接轮询分配给多个 Worker 线程的 Selector——
 * 这正是 Netty 的 bossGroup / workerGroup 结构；并对照解释 Netty 的 Channel、Pipeline、Handler、ByteBuf 概念。
 */
public class ReactorNettyDemo {

    /** Worker：持有一个 Selector，处理分配给它的所有连接的读写。 */
    static class Worker implements Runnable {
        final Selector selector;
        final String name;
        final List<SocketChannel> pending = new ArrayList<>();

        Worker(String name) throws IOException {
            this.name = name;
            this.selector = Selector.open();
        }

        void register(SocketChannel ch) {
            synchronized (pending) {
                pending.add(ch);
            }
            selector.wakeup(); // 唤醒 select()，让新连接得以注册（Netty 用任务队列做同样的事）
        }

        @Override
        public void run() {
            try {
                while (selector.isOpen()) {
                    selector.select(200);
                    synchronized (pending) {
                        for (SocketChannel ch : pending) {
                            ch.configureBlocking(false);
                            ch.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(256));
                        }
                        pending.clear();
                    }
                    var it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey key = it.next();
                        it.remove();
                        if (!key.isValid() || !key.isReadable()) continue;
                        SocketChannel ch = (SocketChannel) key.channel();
                        ByteBuffer buf = (ByteBuffer) key.attachment();
                        int n = ch.read(buf);
                        if (n < 0) {
                            ch.close();
                            continue;
                        }
                        buf.flip();
                        // "pipeline"：解码 -> 业务 -> 编码
                        String msg = StandardCharsets.UTF_8.decode(buf).toString();
                        String reply = "[" + name + "] 处理了: " + msg.toUpperCase();
                        ch.write(ByteBuffer.wrap(reply.getBytes(StandardCharsets.UTF_8)));
                        buf.clear();
                    }
                }
            } catch (IOException | java.nio.channels.ClosedSelectorException e) {
                if (selector.isOpen()) System.out.println("  " + name + " 异常: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 主从 Reactor（1 Boss + 2 Worker） ==");
        Worker[] workers = {new Worker("worker-1"), new Worker("worker-2")};
        for (Worker w : workers) {
            Thread t = new Thread(w, w.name);
            t.setDaemon(true);
            t.start();
        }
        AtomicInteger roundRobin = new AtomicInteger();

        Selector bossSelector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.configureBlocking(false);
        server.register(bossSelector, SelectionKey.OP_ACCEPT);
        int port = ((InetSocketAddress) server.getLocalAddress()).getPort();

        Thread boss = new Thread(() -> {
            try {
                while (bossSelector.isOpen()) {
                    bossSelector.select(200);
                    var it = bossSelector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey key = it.next();
                        it.remove();
                        if (key.isAcceptable()) {
                            SocketChannel client = server.accept();
                            Worker w = workers[roundRobin.getAndIncrement() % workers.length]; // 轮询分配
                            w.register(client);
                        }
                    }
                }
            } catch (IOException | java.nio.channels.ClosedSelectorException e) {
                if (bossSelector.isOpen()) System.out.println("  boss 异常: " + e.getMessage());
            }
        }, "boss");
        boss.setDaemon(true);
        boss.start();

        for (int i = 1; i <= 4; i++) {
            try (SocketChannel client = SocketChannel.open(new InetSocketAddress("127.0.0.1", port))) {
                client.write(ByteBuffer.wrap(("hello-" + i).getBytes(StandardCharsets.UTF_8)));
                ByteBuffer buf = ByteBuffer.allocate(256);
                client.read(buf);
                buf.flip();
                System.out.println("  客户端" + i + " 收到: " + StandardCharsets.UTF_8.decode(buf));
            }
        }
        server.close();
        bossSelector.close();
        for (Worker w : workers) w.selector.close();

        System.out.println("""

                Reactor 三种形态：
                  单 Reactor 单线程：一个线程 accept + read + 业务 + write（Redis 6 之前）
                  单 Reactor 多线程：IO 仍在一个线程，业务交给线程池
                  主从 Reactor：Boss 负责 accept，多个 Worker 各自 Selector 负责 IO（Netty、Nginx）
                Netty 对照：
                  EventLoopGroup bossGroup(1) / workerGroup(N)   <->  本例 boss 线程 / Worker 数组
                  NioEventLoop = 一个线程 + 一个 Selector + 任务队列  <->  Worker.run + pending 列表
                  Channel -> ChannelPipeline -> ChannelHandler 链（入站 decode/业务，出站 encode） <-> 本例 Worker 中的 decode/toUpperCase/write
                  ByteBuf：读写双指针、池化、堆外、引用计数（必须 release）  <-> 本例的 ByteBuffer
                  内置编解码：LengthFieldBasedFrameDecoder、LineBasedFrameDecoder、HttpServerCodec、ProtobufDecoder 解决粘包
                  使用 Netty 的项目：Dubbo、RocketMQ、gRPC-Java、Elasticsearch 传输层、Spring WebFlux(Reactor Netty)
                """);
    }
}
