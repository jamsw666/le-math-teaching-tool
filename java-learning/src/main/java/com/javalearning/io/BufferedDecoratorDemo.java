package com.javalearning.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 知识点 5-2：缓冲流与装饰器模式。
 *
 * <p>重点：无缓冲流每次 read/write 都是一次系统调用；BufferedXxx 用 8KB 缓冲区把 N 次系统调用合并为 1 次；
 * java.io 是装饰器模式的教科书实现（FilterInputStream 包装任意 InputStream 叠加功能），
 * 本例手写一个"统计字节数"的装饰器并层层嵌套。
 */
public class BufferedDecoratorDemo {

    /** 自定义装饰器：在不改变被包装流的前提下增加计数功能。 */
    static class CountingInputStream extends FilterInputStream {
        private long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) count++;
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) count += n;
            return n;
        }

        long count() {
            return count;
        }
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("buffered", ".dat");
        try {
            int size = 5 * 1024 * 1024;
            bufferedVsUnbuffered(file, size);
            decoratorChain(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    static void bufferedVsUnbuffered(Path file, int size) throws IOException {
        System.out.println("== 缓冲 vs 非缓冲（写 " + size / 1024 / 1024 + "MB，逐字节） ==");
        long t = System.nanoTime();
        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
            for (int i = 0; i < size; i++) out.write(i); // 每次都是一次 write 系统调用
        }
        long unbuffered = System.nanoTime() - t;

        t = System.nanoTime();
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file.toFile()))) {
            for (int i = 0; i < size; i++) out.write(i); // 先写入 8KB 缓冲，满了才真正写文件
        }
        long buffered = System.nanoTime() - t;
        System.out.printf("  FileOutputStream %.0f ms，BufferedOutputStream %.0f ms，提速 %.0f 倍%n",
                unbuffered / 1e6, buffered / 1e6, (double) unbuffered / buffered);

        t = System.nanoTime();
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file.toFile()))) {
            while (in.read() != -1) { }
        }
        System.out.printf("  BufferedInputStream 逐字节读 %.0f ms；就算是缓冲流，用 read(byte[]) 批量读仍会更快%n", (System.nanoTime() - t) / 1e6);
        System.out.println("  缓冲区默认 8192 字节；记得 flush()/close()，否则缓冲区里的数据不会落盘");
    }

    static void decoratorChain(Path file) throws IOException {
        System.out.println("\n== 装饰器链 ==");
        Files.writeString(file, "装饰器模式\n让功能可以自由组合\n", StandardCharsets.UTF_8);
        // 文件字节流 -> 计数装饰 -> 缓冲装饰 -> 转字符 -> 按行读取
        try (CountingInputStream counting = new CountingInputStream(new FileInputStream(file.toFile()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(counting), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("  读到: " + line);
            }
            System.out.println("  共读取 " + counting.count() + " 字节");
        }
        System.out.println("""
                结构对照：
                  Component   InputStream
                  Concrete    FileInputStream / ByteArrayInputStream / SocketInputStream（数据源）
                  Decorator   FilterInputStream（持有一个 InputStream 并转发）
                  Concrete    BufferedInputStream / DataInputStream / GZIPInputStream / CountingInputStream（叠加功能）
                相比继承，装饰器可以运行时任意组合：new GZIPInputStream(new BufferedInputStream(new FileInputStream(f)))
                """);
    }
}
