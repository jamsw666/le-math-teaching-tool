package com.javalearning.collections;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 知识点 2-2：Set —— HashSet / LinkedHashSet / TreeSet。
 *
 * <p>重点：三者的底层（HashMap / 链表维护插入顺序 / 红黑树）、去重依赖 equals+hashCode、
 * TreeSet 依赖 compareTo（compareTo 返回 0 即视为重复）、可变对象放入 Set 后修改字段的危险。
 */
public class SetDemo {

    static class Person {
        final String name;
        int age;

        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Person p && p.name.equals(name) && p.age == age;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }

        @Override
        public String toString() {
            return name + "(" + age + ")";
        }
    }

    public static void main(String[] args) {
        System.out.println("== 三种 Set 的顺序特性 ==");
        Set<String> hash = new HashSet<>();
        Set<String> linked = new LinkedHashSet<>();
        Set<String> tree = new TreeSet<>();
        for (String s : new String[]{"banana", "apple", "cherry", "apple"}) {
            hash.add(s);
            linked.add(s);
            tree.add(s);
        }
        System.out.println("HashSet（无序，按 hash 桶）  : " + hash);
        System.out.println("LinkedHashSet（插入顺序）    : " + linked);
        System.out.println("TreeSet（自然排序，红黑树）  : " + tree);

        System.out.println("\n== TreeSet 以 compareTo 判重 ==");
        Set<Person> byName = new TreeSet<>(Comparator.comparing(p -> p.name));
        byName.add(new Person("Tom", 20));
        byName.add(new Person("Tom", 30)); // 名字相同 -> compare 返回 0 -> 被视为重复
        System.out.println("按 name 比较的 TreeSet 只保留一个 Tom: " + byName);
        TreeSet<Integer> sorted = new TreeSet<>(Set.of(10, 5, 20, 15));
        System.out.println("导航方法 floor(12)=" + sorted.floor(12) + " ceiling(12)=" + sorted.ceiling(12)
                + " headSet(15)=" + sorted.headSet(15));

        System.out.println("\n== 可变对象放入 HashSet 后修改字段 ==");
        Set<Person> people = new HashSet<>();
        Person p = new Person("Jerry", 18);
        people.add(p);
        p.age = 19; // hashCode 变了，但对象仍停留在旧桶里
        System.out.println("修改 age 后 contains(p) = " + people.contains(p) + "，remove(p) = " + people.remove(p)
                + "，size = " + people.size() + "  <- 对象\"丢失\"在 Set 中");
        System.out.println("结论：作为 Set 元素 / Map key 的对象应当不可变（推荐用 record）");

        System.out.println("\n== 集合运算 ==");
        Set<Integer> a = new HashSet<>(Set.of(1, 2, 3, 4));
        Set<Integer> b = Set.of(3, 4, 5);
        Set<Integer> union = new HashSet<>(a); union.addAll(b);
        Set<Integer> inter = new HashSet<>(a); inter.retainAll(b);
        Set<Integer> diff = new HashSet<>(a); diff.removeAll(b);
        System.out.println("并集 " + union + " 交集 " + inter + " 差集 " + diff);
    }
}
