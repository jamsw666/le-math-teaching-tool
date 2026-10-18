package com.javalearning.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 知识点 5-5：Socket 与 TCP / HTTP。
 *
 * <p>重点：TCP 三次握手 / 四次挥手与 Socket API 的对应、BIO 每连接一线程模型、
 * 手写一个最小 HTTP/1.1 服务器理解报文结构（请求行、头、空行、体），
 * 用 Java 11 HttpClient 发请求、粘包/拆包问题及解决（长度前缀 / 分隔符）。
 */
public class SocketDemo {

    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            System.out.println("== 最小 HTTP 服务器监听 " + port + " ==");
            ExecutorService pool = Executors.newFixedThreadPool(4);
            Thread acceptor = new Thread(() -> acceptLoop(server, pool), "bio-acceptor");
            acceptor.setDaemon(true);
            acceptor.start();

            rawTcpClient(port);
            httpClient(port);
            pool.shutdownNow();
        }
        System.out.println("""

                TCP 与 Socket API 对应：
                  服务端 new ServerSocket(port) = socket+bind+listen；accept() 在三次握手完成后返回已连接 Socket
                  客户端 new Socket(host, port) 触发三次握手；close() 发送 FIN 进入四次挥手；主动关闭方进入 TIME_WAIT(2MSL)
                  短连接频繁创建会耗尽端口（大量 TIME_WAIT），HTTP/1.1 默认 keep-alive 复用连接
                粘包 / 拆包：TCP 是字节流没有消息边界，应用层需自定义协议——
                  固定长度 / 分隔符（HTTP 头用 \\r\\n）/ 长度前缀（Dubbo、Protobuf）/ Content-Length 或 chunked（HTTP 体）
                BIO 模型：accept 与 read 都阻塞，一连接一线程，万级连接即内存与上下文切换崩溃 -> NIO/Netty（见 5-4、5-6）
                """);
    }

    static void acceptLoop(ServerSocket server, ExecutorService pool) {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();           // 阻塞直到有连接
                pool.execute(() -> handle(socket));        // 每连接交给一个线程
            } catch (IOException e) {
                return;
            }
        }
    }

    /** 解析 HTTP 请求并返回响应：手写一遍就记住了报文格式。 */
    static void handle(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = socket.getOutputStream()) {
            String requestLine = in.readLine();            // GET /hello HTTP/1.1
            if (requestLine == null) return;
            String header;
            StringBuilder headers = new StringBuilder();
            while ((header = in.readLine()) != null && !header.isEmpty()) { // 空行表示头结束
                headers.append("    ").append(header).append('\n');
            }
            System.out.println("  [服务端] 请求行: " + requestLine + "\n  [服务端] 请求头:\n" + headers);
            byte[] body = ("{\"path\":\"" + requestLine.split(" ")[1] + "\",\"thread\":\"" + Thread.currentThread().getName() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            PrintWriter writer = new PrintWriter(out, false, StandardCharsets.UTF_8);
            writer.print("HTTP/1.1 200 OK\r\n");
            writer.print("Content-Type: application/json; charset=utf-8\r\n");
            writer.print("Content-Length: " + body.length + "\r\n");   // 告诉客户端体有多长，解决"读到哪里算结束"
            writer.print("Connection: close\r\n");
            writer.print("\r\n");
            writer.flush();
            out.write(body);
            out.flush();
        } catch (IOException e) {
            System.out.println("  [服务端] 连接异常: " + e.getMessage());
        }
    }

    static void rawTcpClient(int port) throws IOException {
        System.out.println("\n== 用原始 Socket 发送 HTTP 请求 ==");
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(2000);                      // 读超时，避免永久阻塞
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            out.print("GET /raw HTTP/1.1\r\nHost: localhost\r\nUser-Agent: raw-socket\r\n\r\n");
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("  [客户端] " + line);
            }
        }
    }

    static void httpClient(int port) throws Exception {
        System.out.println("\n== Java 11 HttpClient ==");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/users"))
                .timeout(Duration.ofSeconds(2))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("  状态码 " + response.statusCode() + "，Content-Type=" + response.headers().firstValue("Content-Type").orElse("?"));
        System.out.println("  响应体 " + response.body());
        System.out.println("  HttpClient 支持 HTTP/2、异步 sendAsync 返回 CompletableFuture、WebSocket；HttpURLConnection 已过时");
    }
}
