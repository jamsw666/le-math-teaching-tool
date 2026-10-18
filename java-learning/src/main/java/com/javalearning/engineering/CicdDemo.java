package com.javalearning.engineering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 9-6 CI / CD 流水线。
 *
 * <p>手写一个迷你流水线引擎：Stage 顺序执行、Stage 内 Job 并行、失败即停（fail-fast）、
 * 产物（artifact）在阶段间传递、人工审批门禁、以及按分支决定是否执行发布阶段。
 * 真实配置见 ci/github-actions.yml 与 ci/Jenkinsfile。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.engineering.CicdDemo
 */
public class CicdDemo {

    /** 流水线上下文：分支信息 + 阶段间共享的产物。 */
    static class Context {
        final String branch;
        final Map<String, String> artifacts = new ConcurrentHashMap<>();
        final List<String> log = java.util.Collections.synchronizedList(new ArrayList<>());

        Context(String branch) { this.branch = branch; }
    }

    record Job(String name, Function<Context, Boolean> action) {
        Job(String name, Runnable work) { this(name, c -> { work.run(); return true; }); }
    }

    record Stage(String name, List<Job> jobs, java.util.function.Predicate<Context> when) {
        Stage(String name, Job... jobs) { this(name, List.of(jobs), c -> true); }
        Stage onlyOn(String branch) { return new Stage(name, jobs, c -> c.branch.equals(branch)); }
    }

    static class Pipeline {
        private final List<Stage> stages = new ArrayList<>();

        Pipeline stage(Stage s) { stages.add(s); return this; }

        boolean run(Context ctx) {
            long t0 = System.currentTimeMillis();
            for (Stage s : stages) {
                if (!s.when().test(ctx)) {
                    System.out.printf("  ⏭  %-14s skipped (branch=%s)%n", s.name(), ctx.branch);
                    continue;
                }
                System.out.printf("  ▶  %-14s %s%n", s.name(), s.jobs().size() > 1 ? "(" + s.jobs().size() + " jobs 并行)" : "");
                // Stage 内 Job 并行执行，全部成功才进入下一 Stage
                List<CompletableFuture<Boolean>> futures = s.jobs().stream()
                        .map(j -> CompletableFuture.supplyAsync(() -> {
                            long start = System.currentTimeMillis();
                            boolean ok;
                            try {
                                ok = j.action().apply(ctx);
                            } catch (RuntimeException e) {
                                ok = false;
                                ctx.log.add(j.name() + ": " + e.getMessage());
                            }
                            System.out.printf("       %s %-22s %dms%n", ok ? "✔" : "✘", j.name(), System.currentTimeMillis() - start);
                            return ok;
                        }))
                        .toList();
                boolean stageOk = futures.stream().allMatch(CompletableFuture::join);
                if (!stageOk) {
                    System.out.printf("  ✘  流水线在 %s 失败，后续阶段不执行（fail-fast）。耗时 %dms%n", s.name(), System.currentTimeMillis() - t0);
                    ctx.log.forEach(l -> System.out.println("       原因: " + l));
                    return false;
                }
            }
            System.out.printf("  ✔  流水线成功，总耗时 %dms，产物 %s%n", System.currentTimeMillis() - t0, ctx.artifacts);
            return true;
        }
    }

    static Pipeline buildPipeline(boolean testsPass, boolean approve) {
        return new Pipeline()
                .stage(new Stage("checkout", new Job("git clone", () -> work(30))))
                .stage(new Stage("build", new Job("mvn compile", () -> work(80))))
                .stage(new Stage("quality",
                        new Job("unit tests", c -> {
                            work(120);
                            if (!testsPass) throw new IllegalStateException("3 tests failed: OrderServiceTest");
                            return true;
                        }),
                        new Job("checkstyle", () -> work(60)),
                        new Job("dependency scan", () -> work(90))))
                .stage(new Stage("package", new Job("mvn package + docker build", c -> {
                    work(100);
                    c.artifacts.put("image", "registry/demo:" + Integer.toHexString(c.branch.hashCode()));
                    return true;
                })))
                .stage(new Stage("deploy-staging", new Job("kubectl rollout", () -> work(70))).onlyOn("main"))
                .stage(new Stage("approval", new Job("人工审批", c -> {
                    work(20);
                    System.out.println("       审批人决定: " + (approve ? "通过" : "拒绝"));
                    return approve;
                })).onlyOn("main"))
                .stage(new Stage("deploy-prod", new Job("灰度 10% -> 50% -> 100%", () -> work(90))).onlyOn("main"));
    }

    static void work(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        System.out.println("== 场景 1：feature 分支的 PR，测试通过 -> 只到 package 为止 ==");
        buildPipeline(true, true).run(new Context("feature/login"));

        System.out.println("\n== 场景 2：main 分支，单元测试失败 -> 质量门禁拦截，不会发布 ==");
        buildPipeline(false, true).run(new Context("main"));

        System.out.println("\n== 场景 3：main 分支，全部通过 + 人工审批 -> 发布生产 ==");
        buildPipeline(true, true).run(new Context("main"));

        printSummary();
    }

    static void printSummary() {
        System.out.println("""

                概念：
                  CI 持续集成：每次提交自动 编译 -> 单测 -> 静态检查 -> 打包，尽早发现集成问题；核心是“快”（<10 分钟）与“红了必须马上修”
                  CD 持续交付：产物随时可发布，发布需人工按钮；持续部署：通过门禁后自动上生产
                流水线设计要点：
                  - 阶段顺序执行、阶段内并行；fail-fast 节省资源；可缓存的（依赖、Docker 层）尽量缓存
                  - 一次构建、多处部署：镜像在 CI 构建一次，用同一个不可变 tag（commit sha）推到各环境，禁止各环境分别构建
                  - 配置与代码分离（环境变量 / ConfigMap），密钥用平台的 Secret 管理，日志中脱敏
                  - 质量门禁：单测覆盖率阈值（JaCoCo）、Checkstyle / SpotBugs、SonarQube Quality Gate、依赖漏洞扫描（OWASP Dependency-Check / Trivy）
                  - 产物与报告归档：测试报告、覆盖率、SBOM；流水线即代码（Jenkinsfile / .github/workflows 进 Git 审查）
                发布策略：
                  滚动更新（K8s 默认）| 蓝绿（两套环境切流量，回滚快但成本双倍）| 金丝雀 / 灰度（按比例或按用户标签逐步放量，观察指标）| 功能开关（代码已上线但对用户不可见）
                  回滚：kubectl rollout undo / 切回旧版本镜像 tag；数据库变更要向前兼容（先加列后删列，Flyway / Liquibase 管理迁移脚本）
                平台对比：GitHub Actions（与 GitHub 深度集成，Marketplace 丰富）| GitLab CI（.gitlab-ci.yml，自带 Registry）| Jenkins（插件最多，自托管，维护成本高）| Tekton / Argo Workflows（K8s 原生）
                GitOps：ArgoCD / Flux 监听 Git 中的 K8s 清单，集群状态自动向 Git 收敛；发布 = 提 PR 改 tag
                参考：actions/starter-workflows、jenkinsci/pipeline-examples、argoproj/argo-cd、《持续交付》（Jez Humble）
                """);
    }
}
