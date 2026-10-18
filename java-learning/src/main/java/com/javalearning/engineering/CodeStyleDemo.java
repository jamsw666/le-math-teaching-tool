package com.javalearning.engineering;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 9-7 代码规范与静态检查。
 *
 * <p>手写一个迷你 Checkstyle：对本工程源码跑几条规则（行宽、Tab、尾随空格、通配符导入、
 * 空 catch、== 比较字符串、未使用的导入），输出报告；并用一段“反面教材”代码演示
 * SpotBugs / PMD 类工具能发现的 bug 模式。配置样例见 config/checkstyle-sample.xml、.editorconfig。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.engineering.CodeStyleDemo
 */
public class CodeStyleDemo {

    record Violation(String file, int line, String rule, String detail) { }

    /** 规则 = (文件所有行, 文件名) -> 违规列表。 */
    interface Rule extends BiFunction<List<String>, String, List<Violation>> { }

    static final int MAX_LINE = 160;

    static Rule lineRule(String name, Pattern p, String detail) {
        return (lines, file) -> {
            List<Violation> out = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (p.matcher(lines.get(i)).find()) out.add(new Violation(file, i + 1, name, detail));
            }
            return out;
        };
    }

    static final List<Rule> RULES = List.of(
            (lines, file) -> {
                List<Violation> out = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).length() > MAX_LINE) out.add(new Violation(file, i + 1, "LineLength", "超过 " + MAX_LINE + " 列"));
                }
                return out;
            },
            lineRule("FileTabCharacter", Pattern.compile("\t"), "使用了 Tab 缩进"),
            lineRule("TrailingWhitespace", Pattern.compile("\\S[ \\t]+$"), "尾随空格"),
            lineRule("AvoidStarImport", Pattern.compile("^import .*\\.\\*;"), "通配符导入"),
            lineRule("EmptyCatchBlock", Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}"), "空 catch 吞掉异常"),
            lineRule("StringEquality", Pattern.compile("\"[^\"]*\"\\s*[!=]=\\s*\\w|\\w\\s*[!=]=\\s*\"[^\"]*\""), "用 == 比较字符串"),
            CodeStyleDemo::unusedImports);

    static List<Violation> unusedImports(List<String> lines, String file) {
        List<Violation> out = new ArrayList<>();
        String body = String.join("\n", lines);
        Pattern imp = Pattern.compile("^import (static )?([\\w.]+)\\.(\\w+);$");
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = imp.matcher(lines.get(i));
            if (!m.matches()) continue;
            String simple = m.group(3);
            // 粗略：简单名在 import 行之外是否出现过
            int occurrences = 0;
            Matcher use = Pattern.compile("\\b" + Pattern.quote(simple) + "\\b").matcher(body);
            while (use.find()) occurrences++;
            if (occurrences <= 1) out.add(new Violation(file, i + 1, "UnusedImports", "未使用的导入 " + simple));
        }
        return out;
    }

    public static void main(String[] args) throws IOException {
        lintProject();
        bugPatterns();
        printSummary();
    }

    static void lintProject() throws IOException {
        Path root = Files.exists(Path.of("src/main/java")) ? Path.of("src/main/java") : Path.of("java-learning/src/main/java");
        System.out.println("== 迷你 Checkstyle 扫描 " + root.toAbsolutePath().normalize() + " ==");
        List<Violation> all = new ArrayList<>();
        int files = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.filter(x -> x.toString().endsWith(".java")).toList()) {
                files++;
                List<String> lines = Files.readAllLines(p);
                String name = root.relativize(p).toString();
                for (Rule r : RULES) all.addAll(r.apply(lines, name));
            }
        }
        Map<String, Long> byRule = new TreeMap<>();
        all.forEach(v -> byRule.merge(v.rule(), 1L, Long::sum));
        System.out.printf("  扫描 %d 个文件，共 %d 条告警：%s%n", files, all.size(), byRule);
        all.stream().limit(8).forEach(v -> System.out.printf("    %-60s:%-4d [%s] %s%n", v.file(), v.line(), v.rule(), v.detail()));
        if (all.size() > 8) System.out.println("    ... 其余 " + (all.size() - 8) + " 条省略");
        System.out.println("  真实 Checkstyle 基于 AST 而非正则（能区分字符串/注释里的内容），规则 100+，可用 Google / Sun / 阿里 P3C 规则集");
    }

    /** 反面教材：下面每个方法都有一个静态分析工具会报的问题。 */
    @SuppressWarnings("all")
    static class SmellyCode {
        static boolean sameName(String a, String b) {
            return a == b; // SpotBugs ES_COMPARING_STRINGS_WITH_EQ：应该用 equals
        }

        static int parse(String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return 0; // PMD EmptyCatchBlock / AvoidCatchingGenericException 的亲戚：静默吞掉，调用方不知道失败了
            }
        }

        static String concat(List<String> xs) {
            String r = "";
            for (String x : xs) r += x; // PMD UseStringBufferForStringAppends / Sonar：循环中字符串拼接 O(n²)
            return r;
        }

        static Integer box(int v) {
            Integer a = v;
            Integer b = v;
            return a == b ? a : null; // Sonar S4973：Integer 缓存只到 127，-128~127 之外 == 为 false
        }

        static void resource(Path p) throws IOException {
            var in = Files.newInputStream(p); // SpotBugs OBL_UNSATISFIED_OBLIGATION：没有关闭，应 try-with-resources
            in.read();
        }

        static int div(int a, Integer b) {
            return a / b; // SpotBugs NP：b 可能为 null（自动拆箱 NPE）；Sonar：除零
        }
    }

    static void bugPatterns() {
        System.out.println("\n== 静态分析能发现的典型 bug 模式（SmellyCode 类） ==");
        System.out.println("  sameName(new String(\"a\"), new String(\"a\")) = " + SmellyCode.sameName(new String("a"), new String("a")) + "  <- == 比较字符串");
        System.out.println("  box(127) 相等? " + (SmellyCode.box(127) != null) + "   box(128) 相等? " + (SmellyCode.box(128) != null) + "  <- Integer 缓存陷阱");
        System.out.println("  parse(\"abc\") = " + SmellyCode.parse("abc") + "  <- 吞异常后 0 与真实的 0 无法区分");
        System.out.println("  Checkstyle 管“长相”（格式、命名、导入、长度）；PMD 管“写法”（未使用变量、复杂度、坏习惯）；");
        System.out.println("  SpotBugs 分析字节码找“真 bug”（NPE、资源泄漏、线程安全）；SonarQube 聚合三者 + 覆盖率 + 重复率 + 安全热点，给出 Quality Gate");
    }

    static void printSummary() {
        System.out.println("""

                规范来源：
                  《阿里巴巴 Java 开发手册》（泰山版）+ IDEA 插件 P3C | Google Java Style（2 空格缩进，google-java-format 自动格式化）| Oracle Code Conventions（历史）
                命名：类 UpperCamel、方法/变量 lowerCamel、常量 UPPER_SNAKE、包全小写；布尔不以 is 开头的字段（序列化坑）；见名知意、不缩写
                格式自动化：
                  .editorconfig 统一编辑器基础设置（缩进、换行、编码）；Spotless / google-java-format / IDEA Code Style 导入导出；
                  pre-commit hook（husky 风格或 maven git-code-format）在提交前格式化 + 检查，CI 再做一遍兜底
                Maven 集成：maven-checkstyle-plugin（<failOnViolation>true）、maven-pmd-plugin、spotbugs-maven-plugin、jacoco-maven-plugin（覆盖率门禁 <minimum>0.80）、sonar-maven-plugin
                Lombok：减少样板代码，但 @Data 的 equals/hashCode 在 JPA 实体上有坑；Java 16+ 的 record 可替代一部分
                注释：公共 API 写 Javadoc（做什么、参数、返回、异常）；代码里解释“为什么”而非“做了什么”；TODO/FIXME 带负责人与期限
                Code Review 清单：正确性 > 可读性 > 性能；小 PR（< 400 行）；描述动机与影响面；评论区分 nit / 建议 / 必须改
                参考：alibaba/p3c、checkstyle/checkstyle、pmd/pmd、spotbugs/spotbugs、diffplug/spotless、google/google-java-format、SonarSource/sonarqube
                """);
    }
}
