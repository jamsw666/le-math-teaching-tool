package com.javalearning.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 知识点 5-3：序列化与反序列化。
 *
 * <p>重点：Serializable 标记接口、serialVersionUID 的作用、transient 与 static 不被序列化、
 * 父类未实现 Serializable 时会调用其无参构造、readResolve 保护单例、
 * Java 原生序列化的缺点（体积大、慢、安全漏洞）与替代方案（JSON / Protobuf / Kryo）。
 */
public class SerializationDemo {

    static class Session implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        String user;
        transient String password;          // 不参与序列化，反序列化后为 null
        static String region = "cn-east";   // 静态字段属于类，不参与序列化

        Session(String user, String password) {
            this.user = user;
            this.password = password;
        }
    }

    static class Singleton implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        static final Singleton INSTANCE = new Singleton();

        private Singleton() { }

        /** 反序列化时返回已有实例，否则会凭空产生第二个"单例"。 */
        @Serial
        private Object readResolve() {
            return INSTANCE;
        }
    }

    public static void main(String[] args) throws Exception {
        basics();
        singleton();
        sizeAndAlternatives();
    }

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return in.readObject();
        }
    }

    static void basics() throws Exception {
        System.out.println("== 基本用法 ==");
        Session session = new Session("alice", "secret");
        byte[] data = serialize(session);
        Session restored = (Session) deserialize(data);
        System.out.println("  user = " + restored.user + "，transient password = " + restored.password + "，static region = " + Session.region);
        System.out.println("  序列化后 " + data.length + " 字节，Base64 前 40 位: " + Base64.getEncoder().encodeToString(data).substring(0, 40) + "...");
        System.out.println("  serialVersionUID 不一致会抛 InvalidClassException；不显式声明时由编译器根据类结构计算，改个方法就变 -> 务必显式声明");
    }

    static void singleton() throws Exception {
        System.out.println("\n== 单例与 readResolve ==");
        Object restored = deserialize(serialize(Singleton.INSTANCE));
        System.out.println("  反序列化得到的是同一个实例: " + (restored == Singleton.INSTANCE) + "（去掉 readResolve 则为 false；枚举单例天然免疫）");
    }

    static void sizeAndAlternatives() throws Exception {
        System.out.println("\n== 体积对比与替代方案 ==");
        Session s = new Session("bob", "x");
        int javaSize = serialize(s).length;
        String json = "{\"user\":\"bob\"}";
        int jsonSize = json.getBytes(StandardCharsets.UTF_8).length;
        System.out.println("  同样的数据：Java 原生序列化 " + javaSize + " 字节，JSON " + jsonSize + " 字节（原生格式携带类名、字段名、类型描述）");
        System.out.println("""
                Java 原生序列化的问题：
                  1. 体积大、速度慢（反射驱动）
                  2. 只能 Java 之间互通
                  3. 反序列化漏洞：readObject 会执行任意可达类的代码（Apache Commons Collections 事件），Java 9+ 可用 ObjectInputFilter 白名单
                  4. 版本演进脆弱
                生产中的选择：
                  JSON（Jackson / Gson / Fastjson2）        可读、跨语言、HTTP API 首选
                  Protobuf / Thrift / Avro                  二进制、带 schema、体积最小，RPC 与消息队列首选
                  Kryo / Hessian / FST                      Java 内部高性能序列化（Dubbo 默认 Hessian2）
                  Java 17 的 record 与 Jackson 配合即可得到不可变 DTO
                """);
    }
}
