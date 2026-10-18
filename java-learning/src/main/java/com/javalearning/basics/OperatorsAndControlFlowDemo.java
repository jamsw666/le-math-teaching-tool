package com.javalearning.basics;

/**
 * 知识点 1-2：运算符与流程控制。
 *
 * <p>重点：短路运算、位运算的工程用途、switch 的穿透、带标签的 break/continue。
 */
public class OperatorsAndControlFlowDemo {

    public static void main(String[] args) {
        shortCircuit();
        bitOperations();
        switchFallThrough();
        labeledBreak();
    }

    static void shortCircuit() {
        System.out.println("== 短路运算 ==");
        String s = null;
        // && 短路：左侧为 false 时不会求值右侧，避免 NPE
        if (s != null && s.length() > 3) {
            System.out.println("不会执行");
        } else {
            System.out.println("s == null，右侧 s.length() 被短路，没有 NPE");
        }
        // & 不短路：两侧都会求值
        int counter = 0;
        boolean r = (counter++ > 0) & (counter++ > 0);
        System.out.println("使用 & 后 counter = " + counter + "（两侧均求值）, 结果 = " + r);
    }

    static void bitOperations() {
        System.out.println("\n== 位运算的工程用途 ==");
        int flags = 0;
        final int READ = 1, WRITE = 1 << 1, EXEC = 1 << 2;
        flags |= READ | EXEC;
        System.out.println("权限位 flags = " + Integer.toBinaryString(flags)
                + "，是否可写: " + ((flags & WRITE) != 0) + "，是否可执行: " + ((flags & EXEC) != 0));

        // HashMap 用 (n - 1) & hash 代替取模，前提 n 是 2 的幂
        int capacity = 16, hash = 12345;
        System.out.println("hash % 16 = " + (hash % capacity) + "，(16-1) & hash = " + ((capacity - 1) & hash));

        // >> 算术右移保留符号，>>> 逻辑右移补 0
        int negative = -16;
        System.out.println("-16 >> 2 = " + (negative >> 2) + "，-16 >>> 28 = " + (negative >>> 28));

        // 异或交换与找唯一出现的数
        int[] nums = {4, 1, 2, 1, 2};
        int single = 0;
        for (int n : nums) single ^= n;
        System.out.println("数组中只出现一次的数: " + single);
    }

    static void switchFallThrough() {
        System.out.println("\n== switch 穿透 ==");
        int level = 2;
        switch (level) {
            case 1:
                System.out.println("level 1");
            case 2:
                System.out.println("level 2（命中）");
            case 3:
                System.out.println("level 3（因缺少 break 而穿透）");
                break;
            default:
                System.out.println("default");
        }
        // Java 14+ 的箭头语法不会穿透
        String desc = switch (level) {
            case 1 -> "初级";
            case 2 -> "中级";
            default -> "高级";
        };
        System.out.println("箭头语法 switch 表达式: " + desc);
    }

    static void labeledBreak() {
        System.out.println("\n== 带标签的 break ==");
        int[][] matrix = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
        outer:
        for (int[] row : matrix) {
            for (int v : row) {
                if (v == 5) {
                    System.out.println("找到 5，直接跳出两层循环");
                    break outer;
                }
            }
        }
    }
}
