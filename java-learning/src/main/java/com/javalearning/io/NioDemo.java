package com.javalearning.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;

/**
 * 知识点 5-4：NIO —— Channel / Buffer / Selector。
 *
 * <p>重点：Buffer 的 position/limit/capacity 与 flip/clear/compact、堆内 vs 直接缓冲区、
 * FileChannel 读写、Selector 单线程管理多个非阻塞 Channel（IO 多路复用），
 * 本例在同一进程内起一个非阻塞 echo 服务器与客户端演示完整流程。
 */
public class NioDemo {

    public static void main(String[] args) throws Exception {
        bufferBasics();
        fileChannel();
        selectorEcho();
    }

    static void bufferBasics() {
        System.out.println("== ByteBuffer 三指针 ==");
        ByteBuffer buf = ByteBuffer.allocate(8);
        print("allocate(8)        ", buf);
        buf.put("abc".getBytes(StandardCharsets.US_ASCII));
        print("put 3 字节          ", buf);
        buf.flip();                  // 写模式 -> 读模式：limit = position, position = 0
        print("flip()             ", buf);
        byte first = buf.get();
        print("get() 读 1 字节 = " + (char) first + " ", buf);
        buf.compact();               // 未读数据移到开头，position 指向其后，回到写模式
        print("compact()          ", buf);
        buf.clear();                 // 仅重置指针，不清数据
        print("clear()            ", buf);
        ByteBuffer direct = ByteBuffer.allocateDirect(8);
        System.out.println("  isDirect: heap=" + buf.isDirect() + " direct=" + direct.isDirect()
                + "（直接缓冲区在堆外，省去一次内核<->堆内拷贝，分配/回收成本高，适合长期复用的网络缓冲）");
    }

    static void print(String label, ByteBuffer b) {
        System.out.printf("  %s pos=%d lim=%d cap=%d%n", label, b.position(), b.limit(), b.capacity());
    }

    static void fileChannel() throws IOException {
        System.out.println("\n== FileChannel ==");
        Path file = Files.createTempFile("nio", ".txt");
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ch.write(ByteBuffer.wrap("Channel 是双向的，Stream 是单向的".getBytes(StandardCharsets.UTF_8)));
            ch.position(0);
            ByteBuffer buf = ByteBuffer.allocate(128);
            int n = ch.read(buf);
            buf.flip();
            System.out.println("  读到 " + n + " 字节: " + StandardCharsets.UTF_8.decode(buf));
            System.out.println("  FileChannel 还支持 map(内存映射)、transferTo(零拷贝)、lock(文件锁)、force(刷盘)");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    static void selectorEcho() throws Exception {
        System.out.println("\n== Selector 多路复用 echo 服务器 ==");
        Selector selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.configureBlocking(false);                      // 非阻塞是注册到 Selector 的前提
        server.register(selector, SelectionKey.OP_ACCEPT);
        int port = ((InetSocketAddress) server.getLocalAddress()).getPort();
        System.out.println("  服务器监听 127.0.0.1:" + port);

        Thread serverThread = new Thread(() -> runServer(selector), "nio-server");
        serverThread.setDaemon(true);
        serverThread.start();

        for (int i = 1; i <= 2; i++) {
            try (SocketChannel client = SocketChannel.open(new InetSocketAddress("127.0.0.1", port))) {
                client.write(ByteBuffer.wrap(("客户端" + i + " 的消息").getBytes(StandardCharsets.UTF_8)));
                ByteBuffer buf = ByteBuffer.allocate(256);
                client.read(buf);
                buf.flip();
                System.out.println("  客户端" + i + " 收到回显: " + StandardCharsets.UTF_8.decode(buf));
            }
        }
        selector.wakeup();
        server.close();
        System.out.println("  一个线程 + Selector 同时服务多个连接；BIO 则需要每连接一线程。select() 底层是 epoll(Linux)/kqueue(mac)");
    }

    static void runServer(Selector selector) {
        try {
            while (selector.isOpen()) {
                if (selector.select(500) == 0) continue;     // 阻塞直到有就绪事件
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();                                // 必须手动移除，否则下次仍会处理
                    if (key.isAcceptable()) {
                        SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(256));
                    } else if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer buf = (ByteBuffer) key.attachment();
                        int n = client.read(buf);
                        if (n == -1) {
                            client.close();
                            continue;
                        }
                        buf.flip();
                        client.write(buf);                      // 回显
                        buf.clear();
                    }
                }
            }
        } catch (IOException e) {
            if (selector.isOpen()) System.out.println("  服务器异常: " + e.getMessage());
        }
    }
}
