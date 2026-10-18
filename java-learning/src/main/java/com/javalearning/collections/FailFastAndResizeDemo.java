package com.javalearning.collections;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 知识点 2-7：fail-fast 与扩容机制。
 *
 * <p>重点：modCount 如何实现 fail-fast、fail-safe 容器（CopyOnWrite / Concurrent*）的弱一致性、
 * ArrayList 与 HashMap 的扩容时机与倍数（通过反射观察真实容量）、预估容量的性能意义。
 */
public class FailFastAndResizeDemo {

    public static void main(String[] args) throws Exception {
        failFast();
        failSafe();
        arrayListGrowth();
        hashMapGrowth();
    }

    static void failFast() {
        System.out.println("== fail-fast ==");
        List<String> list = new ArrayList<>(List.of("a", "b", "c"));
        try {
            for (String s : list) {
                if (s.equals("a")) list.remove(s);
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("迭代器创建时记录 expectedModCount，每次 next() 检查 modCount != expectedModCount 即抛异常");
        }
        List<String> tricky = new ArrayList<>(List.of("a", "b", "c"));
        for (String s : tricky) {
            if (s.equals("b")) tricky.remove(s); // 删除倒数第二个元素后 hasNext() 直接返回 false，不会抛异常
        }
        System.out.println("经典陷阱：删除倒数第二个元素不抛异常但漏遍历最后一个 -> " + tricky);
        System.out.println("fail-fast 只是尽力而为的 bug 检测，不能依赖它保证并发正确性");
    }

    static void failSafe() {
        System.out.println("\n== fail-safe ==");
        List<String> cow = new CopyOnWriteArrayList<>(List.of("a", "b", "c"));
        for (String s : cow) {
            cow.add(s + "'"); // 写时复制：迭代器遍历的是旧数组快照
        }
        System.out.println("CopyOnWriteArrayList 遍历中添加不报错，但迭代器看不到新元素；最终 " + cow);
        System.out.println("代价：每次写都复制整个数组，适合读多写极少（监听器列表、配置）；ConcurrentHashMap 的迭代器是弱一致的");
    }

    /**
     * Java 16+ 默认强封装 java.base，反射访问 java.util 私有字段需要
     * JVM 参数 {@code --add-opens java.base/java.util=ALL-UNNAMED}；拿不到时退化为按源码规则模拟。
     */
    static Field openField(Class<?> type, String name) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("（无法反射 " + type.getSimpleName() + "." + name + "：" + e.getClass().getSimpleName()
                    + "，加上 --add-opens java.base/java.util=ALL-UNNAMED 可看到真实值，下面按源码规则模拟）");
            return null;
        }
    }

    static void arrayListGrowth() throws Exception {
        System.out.println("\n== ArrayList 扩容 ==");
        ArrayList<Integer> list = new ArrayList<>();
        Field elementData = openField(ArrayList.class, "elementData");
        int lastCap = -1;
        int simulatedCap = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            list.add(i);
            int cap;
            if (elementData != null) {
                cap = ((Object[]) elementData.get(list)).length;
            } else {
                if (list.size() > simulatedCap) simulatedCap = simulatedCap == 0 ? 10 : simulatedCap + (simulatedCap >> 1);
                cap = simulatedCap;
            }
            if (cap != lastCap) {
                sb.append("size=").append(list.size()).append("->cap=").append(cap).append("  ");
                lastCap = cap;
            }
        }
        System.out.println(sb);
        System.out.println("空列表首次 add 分配 10，之后每次扩为 1.5 倍并 Arrays.copyOf 整体搬迁；已知大小时 new ArrayList<>(n) 可避免多次复制");
    }

    static void hashMapGrowth() throws Exception {
        System.out.println("\n== HashMap 扩容 ==");
        HashMap<Integer, Integer> map = new HashMap<>();
        Field table = openField(HashMap.class, "table");
        int lastCap = -1;
        int simulatedCap = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            map.put(i, i);
            int cap;
            if (table != null) {
                Object[] t = (Object[]) table.get(map);
                cap = t == null ? 0 : t.length;
            } else {
                if (simulatedCap == 0) simulatedCap = 16;
                else if (map.size() > simulatedCap * 3 / 4) simulatedCap <<= 1;
                cap = simulatedCap;
            }
            if (cap != lastCap) {
                sb.append("size=").append(map.size()).append("->table=").append(cap).append("  ");
                lastCap = cap;
            }
        }
        System.out.println(sb);
        System.out.println("阈值 = 容量 * 0.75，size 超过阈值时翻倍并 rehash；预估 n 个元素请 new HashMap<>(n / 0.75 + 1)，Guava 的 Maps.newHashMapWithExpectedSize 就是这么做的");
        Map<String, Integer> sized = new HashMap<>(100);
        System.out.println("new HashMap<>(100) 的实际 table 容量会被 tableSizeFor 调整为 2 的幂: " + tableSizeFor(100) + "（示例 size=" + sized.size() + "）");
    }

    static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : n + 1;
    }
}
