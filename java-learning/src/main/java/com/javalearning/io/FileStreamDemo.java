package com.javalearning.io;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 知识点 5-1：File / 字节流 / 字符流。
 *
 * <p>重点：字节流（InputStream/OutputStream）处理一切数据，字符流（Reader/Writer）= 字节流 + 字符编码；
 * 乱码的根源是编码不一致；NIO.2 的 Files / Path 现代 API 应优先于 java.io.File；大文件用 lines() 流式读取。
 */
public class FileStreamDemo {

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("java-learning-io");
        try {
            byteStreams(dir.resolve("bytes.bin"));
            charStreamsAndEncoding(dir.resolve("text.txt"));
            nio2Files(dir);
        } finally {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
            }
        }
    }

    static void byteStreams(Path file) throws IOException {
        System.out.println("== 字节流 ==");
        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
            out.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
            out.write(255); // write(int) 只写低 8 位
        }
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {  // read() 返回 0~255，-1 表示结束（所以返回值是 int 不是 byte）
                sb.append(String.format("%02X ", b));
            }
            System.out.println("  逐字节读取: " + sb + "（图片、音频、class 文件等二进制一律用字节流）");
        }
    }

    static void charStreamsAndEncoding(Path file) throws IOException {
        System.out.println("\n== 字符流与编码 ==");
        String text = "你好, Java IO";
        try (FileWriter writer = new FileWriter(file.toFile(), StandardCharsets.UTF_8)) {
            writer.write(text); // FileWriter = OutputStreamWriter + FileOutputStream，内部做 char -> byte 编码
        }
        try (FileReader reader = new FileReader(file.toFile(), StandardCharsets.UTF_8)) {
            char[] buf = new char[64];
            int n = reader.read(buf);
            System.out.println("  UTF-8 写入再 UTF-8 读取: " + new String(buf, 0, n));
        }
        byte[] gbkBytes = text.getBytes("GBK");
        System.out.println("  同一字符串 UTF-8 占 " + text.getBytes(StandardCharsets.UTF_8).length + " 字节，GBK 占 " + gbkBytes.length + " 字节");
        System.out.println("  GBK 字节按 UTF-8 解码 -> 乱码: " + new String(gbkBytes, StandardCharsets.UTF_8));
        System.out.println("  Java 18 起默认字符集固定为 UTF-8（JEP 400），之前取决于操作系统，这是乱码的历史根源；读写时永远显式指定 Charset");
    }

    static void nio2Files(Path dir) throws IOException {
        System.out.println("\n== NIO.2 Files / Path ==");
        Path file = dir.resolve("lines.txt");
        Files.write(file, List.of("第一行", "第二行", "第三行"), StandardCharsets.UTF_8);
        System.out.println("  Files.readAllLines: " + Files.readAllLines(file));
        System.out.println("  Files.readString: " + Files.readString(file).replace("\n", "\\n"));
        try (Stream<String> lines = Files.lines(file)) {
            System.out.println("  Files.lines 流式读取（大文件不会一次性载入内存），行数 = " + lines.count());
        }
        Files.writeString(file, "追加一行\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        System.out.println("  size=" + Files.size(file) + " bytes，exists=" + Files.exists(file) + "，isDirectory=" + Files.isDirectory(dir));
        Path copy = Files.copy(file, dir.resolve("copy.txt"));
        Files.move(copy, dir.resolve("moved.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (Stream<Path> list = Files.list(dir)) {
            System.out.println("  目录内容: " + list.map(p -> p.getFileName().toString()).sorted().toList());
        }
        System.out.println("  Path 操作: " + Path.of("/a/b/../c.txt").normalize() + "，getFileName=" + file.getFileName()
                + "，相对化=" + dir.relativize(file));
        System.out.println("  java.io.File 的方法失败只返回 false；Files 会抛出带原因的 IOException，且支持符号链接、属性、WatchService");
    }
}
