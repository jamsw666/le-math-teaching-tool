package com.javalearning.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;

/**
 * 知识点 5-7：零拷贝。
 *
 * <p>重点：传统 read+write 文件拷贝需要 4 次拷贝 4 次上下文切换；
 * mmap 减少一次 CPU 拷贝；sendfile（FileChannel.transferTo）只需 2 次 DMA 拷贝；
 * 直接缓冲区避免 JVM 堆与内核之间的拷贝。Kafka、RocketMQ、Netty、Tomcat 都依赖这些技术。
 */
public class ZeroCopyDemo {

    public static void main(String[] args) throws IOException {
        Path src = Files.createTempFile("zero-copy-src", ".bin");
        Path dst1 = Files.createTempFile("zero-copy-dst1", ".bin");
        Path dst2 = Files.createTempFile("zero-copy-dst2", ".bin");
        Path dst3 = Files.createTempFile("zero-copy-dst3", ".bin");
        try {
            int size = 64 * 1024 * 1024;
            byte[] data = new byte[size];
            new Random(1).nextBytes(data);
            Files.write(src, data);
            System.out.println("== 拷贝 " + size / 1024 / 1024 + "MB 文件的三种方式 ==");

            long t = System.nanoTime();
            traditionalCopy(src, dst1);
            System.out.printf("  传统流拷贝（用户态缓冲区中转）: %.0f ms%n", (System.nanoTime() - t) / 1e6);

            t = System.nanoTime();
            transferToCopy(src, dst2);
            System.out.printf("  FileChannel.transferTo（sendfile）: %.0f ms%n", (System.nanoTime() - t) / 1e6);

            t = System.nanoTime();
            mmapCopy(src, dst3);
            System.out.printf("  mmap 内存映射: %.0f ms%n", (System.nanoTime() - t) / 1e6);

            System.out.println("  三份结果一致: " + (Files.mismatch(src, dst1) == -1 && Files.mismatch(src, dst2) == -1 && Files.mismatch(src, dst3) == -1));
        } finally {
            Files.deleteIfExists(src);
            Files.deleteIfExists(dst1);
            Files.deleteIfExists(dst2);
            Files.deleteIfExists(dst3);
        }
        System.out.println("""

                数据路径对比（磁盘 -> 网卡 / 另一文件）：
                  传统 read/write：磁盘 -DMA-> 内核缓冲 -CPU-> 用户缓冲 -CPU-> socket 缓冲 -DMA-> 网卡  （4 次拷贝，4 次上下文切换）
                  mmap + write   ：磁盘 -DMA-> 内核缓冲（映射到用户空间，省一次 CPU 拷贝）-CPU-> socket 缓冲 -DMA-> 网卡  （3 次拷贝）
                  sendfile       ：磁盘 -DMA-> 内核缓冲 -DMA(scatter-gather)-> 网卡  （2 次拷贝，2 次切换，CPU 不参与搬运）
                Java API：
                  FileChannel.transferTo/transferFrom -> sendfile（单次最多 2GB，大文件需循环）
                  FileChannel.map -> mmap，返回 MappedByteBuffer，适合随机读写大文件（RocketMQ CommitLog）
                  ByteBuffer.allocateDirect -> 堆外内存，Socket 写出时避免 堆 -> 临时直接缓冲 的拷贝（Netty 默认池化直接内存）
                应用：Kafka 消费端用 sendfile 把日志段直接发到网卡；Netty FileRegion；Tomcat/Nginx 静态文件 sendfile on
                """);
    }

    static void traditionalCopy(Path src, Path dst) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(src.toFile()), 64 * 1024);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dst.toFile()), 64 * 1024)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    static void transferToCopy(Path src, Path dst) throws IOException {
        try (FileChannel in = FileChannel.open(src, StandardOpenOption.READ);
             FileChannel out = FileChannel.open(dst, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            long position = 0, size = in.size();
            while (position < size) {
                position += in.transferTo(position, size - position, out); // 可能一次没传完，需要循环
            }
        }
    }

    static void mmapCopy(Path src, Path dst) throws IOException {
        try (FileChannel in = FileChannel.open(src, StandardOpenOption.READ);
             FileChannel out = FileChannel.open(dst, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            long size = in.size();
            MappedByteBuffer source = in.map(FileChannel.MapMode.READ_ONLY, 0, size);
            MappedByteBuffer target = out.map(FileChannel.MapMode.READ_WRITE, 0, size);
            target.put(source);
            target.force(); // 刷到磁盘
        }
    }
}
