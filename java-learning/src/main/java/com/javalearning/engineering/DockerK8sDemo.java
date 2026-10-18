package com.javalearning.engineering;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 9-5 Docker / Kubernetes 部署。
 *
 * <p>读取工程根目录的 Dockerfile 逐条解释指令并模拟“层缓存”（每层 id = hash(上层 id + 指令)，
 * 改动哪一行，其后所有层失效）；读取 deploy/k8s.yaml 提取资源对象并解释；
 * 最后用 Runtime API 展示 JVM 的容器感知（cgroup 内看到的 CPU / 内存）。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.engineering.DockerK8sDemo
 */
public class DockerK8sDemo {

    public static void main(String[] args) throws Exception {
        dockerfileWalkthrough();
        layerCache();
        k8sManifest();
        jvmInContainer();
        printSummary();
    }

    static Path find(String name) {
        for (Path p : List.of(Path.of(name), Path.of("java-learning", name))) {
            if (Files.exists(p)) return p;
        }
        return null;
    }

    static void dockerfileWalkthrough() throws IOException {
        System.out.println("== Dockerfile 逐条解读 ==");
        Path p = find("Dockerfile");
        if (p == null) {
            System.out.println("  未找到 Dockerfile，请在 java-learning 目录运行");
            return;
        }
        Map<String, String> explain = new LinkedHashMap<>();
        explain.put("FROM", "基础镜像；AS 命名阶段，多阶段构建最终镜像只含最后一个 FROM 之后的内容");
        explain.put("WORKDIR", "切换工作目录（不存在会创建），后续 COPY/RUN 的相对路径基准");
        explain.put("COPY", "拷贝文件进镜像，产生新层；--from=阶段 从前一阶段拷贝产物");
        explain.put("RUN", "构建时执行命令，产生新层；多个命令用 && 合并减少层数");
        explain.put("ENV", "环境变量，构建时与运行时都可见");
        explain.put("USER", "后续指令与容器进程的运行用户，安全基线：不用 root");
        explain.put("EXPOSE", "声明端口（文档作用，真正映射靠 docker run -p）");
        explain.put("ENTRYPOINT", "容器启动命令（不易被覆盖）；exec 形式 [\"a\",\"b\"] 让 Java 成为 PID 1 直接收到 SIGTERM");
        explain.put("CMD", "ENTRYPOINT 的默认参数，docker run 后面的参数会覆盖它");
        explain.put("HEALTHCHECK", "容器级健康检查；K8s 里通常改用 probe");
        for (String line : Files.readAllLines(p)) {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String instr = t.split("\\s+")[0];
            if (!explain.containsKey(instr)) continue;
            System.out.printf("  %-11s %s%n", instr, truncate(t, 70));
            System.out.printf("  %-11s ↳ %s%n", "", explain.remove(instr));
        }
    }

    static void layerCache() throws IOException {
        System.out.println("\n== 层缓存：每层 id 由“上层 id + 本层指令 + 拷贝内容”决定 ==");
        List<String> steps = List.of(
                "FROM maven:3.9-eclipse-temurin-21",
                "COPY pom.xml .",
                "RUN mvn dependency:go-offline",
                "COPY src ./src",
                "RUN mvn package");
        List<String> first = layerIds(steps, "pom-v1", "src-v1");
        List<String> second = layerIds(steps, "pom-v1", "src-v2"); // 只改了源码
        List<String> third = layerIds(steps, "pom-v2", "src-v2"); // 改了 pom
        for (int i = 0; i < steps.size(); i++) {
            String s1 = first.get(i).equals(second.get(i)) ? "CACHED" : "rebuild";
            String s2 = first.get(i).equals(third.get(i)) ? "CACHED" : "rebuild";
            System.out.printf("  %-36s 改源码: %-8s 改 pom: %s%n", steps.get(i), s1, s2);
        }
        System.out.println("  所以“先 COPY pom 下载依赖，再 COPY src”比一次性 COPY . 快得多：日常改代码不会重新下载依赖");
        System.out.println("  .dockerignore 排除 target/ .git/ 等，减少构建上下文传输与无意义的缓存失效");
    }

    static List<String> layerIds(List<String> steps, String pomContent, String srcContent) {
        List<String> ids = new ArrayList<>();
        String parent = "scratch";
        for (String step : steps) {
            String content = step.contains("pom.xml") ? pomContent : step.contains("src") ? srcContent : "";
            parent = sha256(parent + "|" + step + "|" + content).substring(0, 12);
            ids.add(parent);
        }
        return ids;
    }

    static void k8sManifest() throws IOException {
        System.out.println("\n== deploy/k8s.yaml 中的资源对象 ==");
        Path p = find("deploy/k8s.yaml");
        if (p == null) {
            System.out.println("  未找到 deploy/k8s.yaml");
            return;
        }
        Map<String, String> why = Map.of(
                "ConfigMap", "非敏感配置，环境变量或挂载为文件，与镜像解耦（同一镜像跑多环境）",
                "Secret", "敏感配置，base64 存储（不是加密），需配合 RBAC / 加密存储",
                "Deployment", "声明期望副本数与更新策略，ReplicaSet 保证副本，Pod 挂了自动拉起 => 自愈",
                "Service", "为一组 Pod 提供稳定的虚拟 IP / DNS 名，做 4 层负载均衡",
                "Ingress", "7 层入口：域名 / 路径路由到 Service，由 Nginx / Traefik 等 Ingress Controller 实现",
                "HorizontalPodAutoscaler", "按 CPU / 内存 / 自定义指标自动伸缩副本数");
        String kind = null;
        for (String line : Files.readAllLines(p)) {
            if (line.startsWith("kind:")) {
                kind = line.substring(5).strip();
            } else if (kind != null && line.strip().startsWith("name:") && line.startsWith("  name:")) {
                System.out.printf("  %-24s %-14s %s%n", kind, line.strip().substring(5).strip(), why.getOrDefault(kind, ""));
                kind = null;
            }
        }
        System.out.println("""
                  三个探针：startupProbe（启动期兜底）→ livenessProbe（失败重启）→ readinessProbe（失败摘流不重启）
                  零停机发布：maxUnavailable=0 + readiness 通过才接流 + preStop sleep + 优雅停机 + terminationGracePeriodSeconds
                  resources.limits.memory 决定 JVM 能用多少（MaxRAMPercentage 按它算），超了直接 OOMKilled（exit 137）""");
    }

    static void jvmInContainer() {
        System.out.println("\n== JVM 容器感知（在容器里运行会看到 cgroup 限制而非宿主机资源） ==");
        Runtime rt = Runtime.getRuntime();
        System.out.printf("  availableProcessors=%d  maxMemory=%d MB  （JDK 10+ 默认 -XX:+UseContainerSupport）%n",
                rt.availableProcessors(), rt.maxMemory() / 1024 / 1024);
        System.out.println("  线程池、ForkJoinPool、GC 线程数都基于 availableProcessors；limits.cpu=500m 时它是 1，别再 new 几十个线程");
        System.out.println("  堆大小：不写 -Xmx 时默认 1/4 物理内存（容器里即 limit），建议 -XX:MaxRAMPercentage=75 并留出堆外（Metaspace、线程栈、Netty 直接内存）");
    }

    static void printSummary() {
        System.out.println("""

                Docker 三要素：镜像（只读分层，UnionFS）/ 容器（镜像 + 可写层 + 进程，Namespace 隔离 + cgroup 限额）/ 仓库（Registry）
                常用命令：docker build -t name:tag . | run -d -p 8080:8080 --name x -e K=V -v host:ctr | logs -f | exec -it x sh | ps / images / rm / rmi / system prune
                镜像瘦身：多阶段构建、JRE 而非 JDK、alpine / distroless、jlink 裁剪模块、Spring Boot 分层 jar（layertools）让依赖层与代码层分开
                一键编排：docker compose（本地起 app + mysql + redis），Testcontainers 在集成测试里用 Docker 起真实依赖
                K8s 核心对象：Pod（最小调度单位，一个或多个容器共享网络与存储）< ReplicaSet < Deployment；StatefulSet（有状态，稳定网络标识）；DaemonSet（每节点一个）；Job / CronJob
                网络：Service（ClusterIP / NodePort / LoadBalancer）+ Ingress；CoreDNS 服务发现 svc-name.namespace.svc.cluster.local
                配置：ConfigMap / Secret；存储：PV / PVC / StorageClass；命名空间 Namespace 隔离环境；RBAC 权限
                Java 服务上云清单：优雅停机、健康端点（Spring Boot Actuator）、结构化日志到 stdout、指标 /actuator/prometheus、不把状态存本地磁盘、12-Factor
                Helm 打包 K8s 清单成 Chart（模板 + values），Kustomize 做环境叠加；GitOps（ArgoCD / Flux）以 Git 为唯一事实来源自动同步集群
                参考：docker/docs、kubernetes/kubernetes、GoogleContainerTools/distroless、spring-projects/spring-boot（分层 jar 与 buildpacks）、《Kubernetes in Action》
                """);
    }

    static String truncate(String s, int n) { return s.length() <= n ? s : s.substring(0, n) + "..."; }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
