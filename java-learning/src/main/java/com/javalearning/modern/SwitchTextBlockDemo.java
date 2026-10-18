package com.javalearning.modern;

import java.time.DayOfWeek;

/**
 * 知识点 6-5：Switch 表达式与文本块（Java 14 / 15）。
 *
 * <p>重点：箭头形式不穿透、switch 作为表达式返回值、多标签 case、yield 在代码块中返回值、
 * 表达式必须穷尽（枚举/密封类可省 default）；文本块的三引号、缩进剥离规则、转义 \ 与 \s、formatted()。
 */
public class SwitchTextBlockDemo {

    enum Level { LOW, MEDIUM, HIGH }

    public static void main(String[] args) {
        switchExpression();
        textBlock();
    }

    static void switchExpression() {
        System.out.println("== switch 表达式 ==");
        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.WEDNESDAY}) {
            String type = switch (day) {
                case SATURDAY, SUNDAY -> "周末";                    // 多标签
                case WEDNESDAY -> {                                 // 代码块用 yield 返回
                    String s = "周" + day.getValue();
                    yield s + "（小周末）";
                }
                default -> "工作日";
            };
            System.out.println("  " + day + " -> " + type);
        }

        Level level = Level.HIGH;
        int score = switch (level) {    // 覆盖所有枚举常量时不需要 default，新增枚举值会得到编译错误而不是静默走 default
            case LOW -> 1;
            case MEDIUM -> 5;
            case HIGH -> 10;
        };
        System.out.println("  枚举穷尽匹配，HIGH -> " + score);

        // 旧语法也可以作为表达式使用，但需要 yield 且小心穿透
        int legacy = switch (3) {
            case 1:
            case 2:
                yield 10;
            default:
                yield 30;
        };
        System.out.println("  冒号语法 + yield: " + legacy);
        System.out.println("  箭头语法：无穿透、无需 break、右侧可以是表达式 / 代码块 / throw");
    }

    static void textBlock() {
        System.out.println("\n== 文本块 ==");
        String json = """
                {
                  "name": "java",
                  "version": 21,
                  "tags": ["modern", "fast"]
                }
                """;
        System.out.print("  JSON 文本块（保留换行与相对缩进，公共缩进被剥离）:\n" + json.indent(4));

        String sql = """
                SELECT id, name
                FROM users
                WHERE status = 'ACTIVE' \
                AND age > 18
                """;
        System.out.println("  行尾 \\ 表示续行不换行: " + sql.replace("\n", "⏎"));

        String html = """
                <p>末尾空格会被去掉，\
                用 \\s 保留一个空格</p>\s
                """;
        System.out.println("  \\s 保留尾部空格（␣ 表示空格）: [" + html.replace(" \n", "␣⏎") + "]");

        String template = """
                Hello, %s! 你有 %d 条新消息。
                """.formatted("Alice", 3);
        System.out.print("  formatted(): " + template);

        System.out.println("""
                规则：
                  开头的 \"\"\" 后必须换行；内容的最小公共缩进由所有非空行 + 结束 \"\"\" 的位置决定
                  结束 \"\"\" 单独一行 -> 字符串以换行结尾；紧跟在内容后 -> 不带换行
                  内部可以直接写 " 和 '，三个连续引号需要转义 \\\"\"\"
                  适合 SQL、JSON、HTML、正则、多行日志模板，比 "..." + "\\n" + 拼接可读得多
                """);
    }
}
