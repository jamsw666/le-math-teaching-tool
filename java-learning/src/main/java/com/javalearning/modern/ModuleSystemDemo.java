package com.javalearning.modern;

import java.lang.module.ModuleDescriptor;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * 知识点 6-3：模块化系统 JPMS（Java 9）。
 *
 * <p>重点：module-info.java 的五种指令（requires / exports / opens / uses / provides）、
 * 强封装与 --add-opens、未命名模块与自动模块、jlink 定制运行时、ServiceLoader 与 provides/uses 的结合。
 * 本工程本身是类路径上的"未命名模块"，示例通过反射 API 观察 JDK 模块并打印一个 module-info 样板。
 */
public class ModuleSystemDemo {

    public static void main(String[] args) {
        System.out.println("== 当前类所在模块 ==");
        Module self = ModuleSystemDemo.class.getModule();
        System.out.println("  isNamed=" + self.isNamed() + "（类路径上的代码属于未命名模块，可以读取所有模块）");

        System.out.println("\n== JDK 模块 java.base ==");
        Module base = String.class.getModule();
        ModuleDescriptor desc = base.getDescriptor();
        System.out.println("  名称: " + desc.name() + "，导出包数量: " + desc.exports().size());
        System.out.println("  部分导出包: " + desc.exports().stream().map(ModuleDescriptor.Exports::source).sorted().limit(6).collect(Collectors.joining(", ")) + " ...");
        System.out.println("  java.base 对未命名模块开放 java.util 反射吗? " + base.isOpen("java.util", self)
                + "  <- 这就是 FailFastAndResizeDemo 反射 ArrayList 内部字段失败的原因");
        System.out.println("  java.sql 依赖: " + java.sql.Connection.class.getModule().getDescriptor().requires().stream()
                .map(ModuleDescriptor.Requires::name).sorted().collect(Collectors.joining(", ")));

        System.out.println("\n== ServiceLoader（SPI） ==");
        int drivers = 0;
        for (java.sql.Driver d : ServiceLoader.load(java.sql.Driver.class)) {
            System.out.println("  发现 JDBC 驱动: " + d.getClass().getName());
            drivers++;
        }
        if (drivers == 0) System.out.println("  类路径上没有 JDBC 驱动（通过 Maven 运行时会看到 H2 的 org.h2.Driver）");

        System.out.println("""

                module-info.java 样板（放在源码根目录）：
                ----------------------------------------------------------
                module com.example.order {
                    requires java.sql;                        // 依赖模块（编译 + 运行）
                    requires transitive com.example.common;   // 传递依赖：使用者自动 requires
                    requires static lombok;                   // 仅编译期需要
                    exports com.example.order.api;            // 对所有模块公开（仅 public 类型）
                    exports com.example.order.spi to com.example.plugin;  // 限定导出
                    opens com.example.order.entity;           // 允许运行时深度反射（JPA / Jackson 需要）
                    uses com.example.order.spi.PaymentProvider;           // 消费 SPI
                    provides com.example.order.spi.PaymentProvider with com.example.order.AlipayProvider; // 提供实现
                }
                ----------------------------------------------------------
                要点：
                  强封装：未 exports 的包即使 public 也不可访问；未 opens 的包不能 setAccessible -> 迁移时常用 --add-opens / --add-exports 过渡
                  三类模块：命名模块（有 module-info）、自动模块（类路径 jar 放到模块路径，名字来自 Automatic-Module-Name 或文件名）、未命名模块（类路径）
                  jlink --add-modules java.base,java.sql --output runtime  可裁剪出几十 MB 的最小运行时（容器镜像瘦身）
                  jdeps --jdk-internals app.jar  分析对内部 API 的依赖
                  现实：大多数业务项目仍跑在类路径上；库作者至少应提供 Automatic-Module-Name
                """);
    }
}
