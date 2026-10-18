package com.javalearning.engineering;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 9-1 Maven / Gradle 构建与依赖管理。
 *
 * <p>读取本工程自己的 pom.xml 并解析坐标、依赖与 scope，演示 Maven 生命周期、
 * 依赖传递与"最短路径优先"仲裁规则（手写一个小的依赖树解析器），并对照 Gradle 写法。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.engineering.BuildToolsDemo
 */
public class BuildToolsDemo {

    public static void main(String[] args) throws Exception {
        parsePom();
        lifecycle();
        dependencyMediation();
        gradleComparison();
        printSummary();
    }

    static void parsePom() throws Exception {
        Path pom = Path.of("pom.xml");
        if (!Files.exists(pom)) {
            pom = Path.of("java-learning/pom.xml");
        }
        System.out.println("== 解析 " + pom.toAbsolutePath().normalize() + " ==");
        if (!Files.exists(pom)) {
            System.out.println("  （未找到 pom.xml，请在 java-learning 目录下运行）");
            return;
        }
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        Document doc = f.newDocumentBuilder().parse(pom.toFile());
        Element project = doc.getDocumentElement();
        System.out.printf("  坐标 GAV: %s:%s:%s  packaging=%s%n",
                text(project, "groupId"), text(project, "artifactId"), text(project, "version"), text(project, "packaging"));

        Map<String, String> props = new LinkedHashMap<>();
        NodeList propNodes = project.getElementsByTagName("properties");
        if (propNodes.getLength() > 0) {
            NodeList children = propNodes.item(0).getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element e) {
                    props.put(e.getTagName(), e.getTextContent().trim());
                }
            }
        }
        System.out.println("  properties: " + props);

        System.out.println("  依赖（scope 决定出现在哪个类路径）：");
        NodeList deps = project.getElementsByTagName("dependency");
        for (int i = 0; i < deps.getLength(); i++) {
            Element d = (Element) deps.item(i);
            String version = text(d, "version");
            // 解析 ${xxx} 占位符
            if (version.startsWith("${")) {
                version = props.getOrDefault(version.substring(2, version.length() - 1), version) + " (来自 properties)";
            }
            String scope = text(d, "scope").isEmpty() ? "compile" : text(d, "scope");
            System.out.printf("    %-22s %-25s %-28s scope=%s%n", text(d, "groupId"), text(d, "artifactId"), version, scope);
        }
        System.out.println("""
                  scope 含义：compile 编译+测试+运行都在；provided 编译在、运行由容器提供（servlet-api、lombok）；
                             runtime 编译不在、运行在（JDBC 驱动、日志实现）；test 仅测试；import 仅 dependencyManagement 用于导入 BOM""");
    }

    static void lifecycle() {
        System.out.println("\n== Maven 三套生命周期 ==");
        String[] defaultPhases = {"validate", "compile", "test", "package", "verify", "install", "deploy"};
        System.out.println("  default: " + String.join(" -> ", defaultPhases) + "（执行某阶段会先执行它前面所有阶段）");
        System.out.println("  clean:   pre-clean -> clean -> post-clean      site: pre-site -> site -> post-site -> site-deploy");
        System.out.println("  阶段绑定插件目标：compile -> maven-compiler-plugin:compile；test -> surefire:test；package -> jar:jar");
        System.out.println("  `mvn clean package -DskipTests`：clean 生命周期到 clean + default 生命周期到 package，跳过测试执行（-Dmaven.test.skip 连编译都跳）");
        System.out.println("  本工程 exec:java 是直接调用插件目标而非阶段，格式 plugin:goal");
    }

    /** 手写依赖树解析：模拟 Maven 的传递依赖 + 最短路径优先 + 先声明优先。 */
    record Artifact(String name, String version, List<Artifact> deps) {
        Artifact(String name, String version, Artifact... deps) {
            this(name, version, List.of(deps));
        }
        String gav() { return name + ":" + version; }
    }

    static void dependencyMediation() {
        System.out.println("\n== 依赖仲裁：最短路径优先，路径相同先声明优先 ==");
        Artifact jacksonOld = new Artifact("jackson", "2.13");
        Artifact jacksonNew = new Artifact("jackson", "2.17");
        Artifact springWeb = new Artifact("spring-web", "6.1", jacksonNew);
        Artifact someSdk = new Artifact("some-sdk", "1.0", new Artifact("sdk-core", "1.0", jacksonOld));
        Artifact myApp = new Artifact("my-app", "1.0", someSdk, springWeb);

        Map<String, String> resolved = new LinkedHashMap<>();
        Map<String, Integer> depth = new LinkedHashMap<>();
        List<String> tree = new ArrayList<>();
        walk(myApp, 0, "", resolved, depth, tree);
        tree.forEach(System.out::println);
        System.out.println("  解析结果: " + resolved);
        System.out.println("  jackson 有 2.13（深度 3）和 2.17（深度 2）两个版本 -> 选路径更短的 2.17；若 some-sdk 其实依赖 2.13 的特有 API 就会运行时 NoSuchMethodError");
        System.out.println("  治理手段：<dependencyManagement> 锁版本（Spring Boot 的 BOM 就是这个）、<exclusions> 排除、mvn dependency:tree -Dverbose 看冲突");
        System.out.println("  Gradle 默认策略不同：取最高版本（2.17），可用 resolutionStrategy.force / 严格版本覆盖");
    }

    static void walk(Artifact a, int d, String indent, Map<String, String> resolved, Map<String, Integer> depth, List<String> out) {
        String mark = "";
        if (d > 0) {
            Integer prev = depth.get(a.name());
            if (prev == null || d < prev) {
                if (prev != null) mark = "  (覆盖了深度 " + prev + " 的 " + resolved.get(a.name()) + ")";
                resolved.put(a.name(), a.version());
                depth.put(a.name(), d);
            } else {
                mark = "  (omitted for conflict with " + resolved.get(a.name()) + ")";
            }
        }
        out.add("  " + indent + (d == 0 ? "" : "+- ") + a.gav() + mark);
        for (Artifact dep : a.deps()) {
            walk(dep, d + 1, indent + "   ", resolved, depth, out);
        }
    }

    static void gradleComparison() {
        System.out.println("\n== 同样的配置用 Gradle (Kotlin DSL) 写 ==");
        System.out.println("""
                  plugins { java; application }
                  java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
                  repositories { mavenCentral() }
                  dependencies {
                      implementation("com.h2database:h2:2.2.224")        // ≈ compile，但不传递给下游的编译类路径（api 才传递）
                      runtimeOnly("org.slf4j:slf4j-simple:2.0.13")       // ≈ runtime
                      testImplementation(platform("org.junit:junit-bom:5.10.2"))
                      testImplementation("org.junit.jupiter:junit-jupiter")
                  }
                  tasks.test { useJUnitPlatform() }
                  application { mainClass.set("com.javalearning.Main") }
                Gradle 特点：Groovy/Kotlin 脚本灵活、任务图增量构建 + 构建缓存 + 守护进程 => 大项目更快；Android 官方构建工具
                Maven 特点：XML 声明式、约定统一、生态稳定、IDE 支持好 => 传统 Java 后端更常见
                """);
    }

    static void printSummary() {
        System.out.println("""
                多模块工程：父 pom <packaging>pom</packaging> + <modules>；子模块 <parent> 继承 properties / dependencyManagement / pluginManagement
                仓库顺序：本地 ~/.m2/repository -> settings.xml 的 mirror（国内配阿里云镜像）-> 远程仓库；私服 Nexus/Artifactory 统一出口
                版本规范：语义化 MAJOR.MINOR.PATCH；-SNAPSHOT 每次构建都去远程检查更新，发布版本不可变
                常用命令：mvn dependency:tree | mvn help:effective-pom | mvn versions:display-dependency-updates | mvn -o 离线 | mvn -pl module -am 只构建某模块及其依赖
                构建可重复：固定插件版本、锁依赖版本、不用 LATEST/RELEASE、CI 用 ./mvnw（Maven Wrapper）保证 Maven 版本一致
                参考：apache/maven、gradle/gradle、《Maven 实战》、Spring Boot 的 spring-boot-dependencies BOM
                """);
    }

    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i).getParentNode() == parent) {
                return nl.item(i).getTextContent().trim();
            }
        }
        return "";
    }
}
