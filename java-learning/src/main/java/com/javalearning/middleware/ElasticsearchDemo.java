package com.javalearning.middleware;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 8-5 Elasticsearch。
 *
 * <p>手写倒排索引 + TF-IDF 相关性评分，演示 ES 为什么全文检索比 MySQL LIKE 快、
 * 分词器的作用、bool 查询（must/should/filter）、聚合（terms），以及分片路由。
 *
 * <p>运行：mvn -q exec:java -Ddemo=com.javalearning.middleware.ElasticsearchDemo
 */
public class ElasticsearchDemo {

    record Doc(int id, String title, String category, int price) { }

    /** 倒排索引：term -> (docId -> 词频)。ES 底层 Lucene 把 term 字典做成 FST，倒排表用 Roaring Bitmap / 跳表压缩。 */
    static class InvertedIndex {
        private final Map<String, Map<Integer, Integer>> postings = new TreeMap<>();
        private final Map<Integer, Doc> docs = new LinkedHashMap<>();
        private final Map<Integer, Integer> docLength = new HashMap<>();

        void index(Doc d) {
            docs.put(d.id(), d);
            List<String> tokens = analyze(d.title());
            docLength.put(d.id(), tokens.size());
            for (String t : tokens) {
                postings.computeIfAbsent(t, k -> new TreeMap<>()).merge(d.id(), 1, Integer::sum);
            }
        }

        /** 简化版 analyzer：小写 -> 按非字母数字切分 -> 去停用词。中文需 IK 等分词器。 */
        static List<String> analyze(String text) {
            Set<String> stop = Set.of("the", "a", "of", "and", "for");
            List<String> out = new ArrayList<>();
            for (String t : Pattern.compile("[^a-z0-9\\u4e00-\\u9fa5]+").split(text.toLowerCase(Locale.ROOT))) {
                if (!t.isEmpty() && !stop.contains(t)) out.add(t);
            }
            return out;
        }

        /** match 查询：对 query 分词，任一词命中即召回（OR），按 TF-IDF 打分排序。 */
        List<Map.Entry<Doc, Double>> match(String query) {
            Map<Integer, Double> scores = new HashMap<>();
            int n = docs.size();
            for (String term : analyze(query)) {
                Map<Integer, Integer> list = postings.getOrDefault(term, Map.of());
                double idf = Math.log(1 + (n - list.size() + 0.5) / (list.size() + 0.5)); // BM25 风格 idf
                for (var e : list.entrySet()) {
                    double tf = (double) e.getValue() / docLength.get(e.getKey());
                    scores.merge(e.getKey(), tf * idf, Double::sum);
                }
            }
            return scores.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .map(e -> Map.entry(docs.get(e.getKey()), e.getValue()))
                    .toList();
        }

        Set<Integer> termDocs(String term) { return postings.getOrDefault(term, Map.of()).keySet(); }

        Map<String, Map<Integer, Integer>> postings() { return postings; }

        Doc doc(int id) { return docs.get(id); }
    }

    public static void main(String[] args) {
        InvertedIndex idx = new InvertedIndex();
        List<Doc> data = List.of(
                new Doc(1, "Java Concurrency in Practice", "book", 89),
                new Doc(2, "Effective Java Third Edition", "book", 79),
                new Doc(3, "Java Virtual Machine Specification", "book", 99),
                new Doc(4, "Redis in Action", "book", 69),
                new Doc(5, "Java Java Java: Object-Oriented Problem Solving", "course", 199),
                new Doc(6, "Designing Data-Intensive Applications", "book", 109),
                new Doc(7, "Spring in Action", "course", 159)
        );
        data.forEach(idx::index);

        showIndex(idx);
        fullTextSearch(idx);
        boolQuery(idx, data);
        aggregation(data);
        shardRouting();
        printSummary();
    }

    static void showIndex(InvertedIndex idx) {
        System.out.println("== 倒排索引（term -> {docId=词频}） ==");
        idx.postings().forEach((t, p) -> {
            if (p.size() > 1 || t.equals("redis")) System.out.printf("  %-12s -> %s%n", t, p);
        });
        System.out.println("  （只展示部分 term）MySQL LIKE '%java%' 需逐行扫描做字符串匹配；倒排索引直接按词定位文档列表，复杂度与匹配文档数相关而非总行数");
    }

    static void fullTextSearch(InvertedIndex idx) {
        System.out.println("\n== match 查询 \"java in action\" 并按相关性评分排序 ==");
        for (var e : idx.match("java in action")) {
            System.out.printf("  score=%.3f  #%d %s%n", e.getValue(), e.getKey().id(), e.getKey().title());
        }
        System.out.println("  评分思想（BM25）：词在文档中出现越多(TF)越相关；词在越少文档中出现(IDF)越重要；文档越短同等词频权重越高");
        System.out.println("  \"Java Java Java\" 虽然 TF 高但文档也长，被长度归一化压制；\"in\" 几乎每本都有，IDF 低贡献小");
    }

    static void boolQuery(InvertedIndex idx, List<Doc> data) {
        System.out.println("\n== bool 查询: must(match java) + filter(category=book, price<=90) ==");
        Set<Integer> must = new HashSet<>(idx.termDocs("java"));
        Set<Integer> filter = new HashSet<>();
        for (Doc d : data) {
            if (d.category().equals("book") && d.price() <= 90) filter.add(d.id());
        }
        must.retainAll(filter);
        must.stream().sorted().forEach(id -> System.out.println("  " + idx.doc(id)));
        System.out.println("  must/should 参与评分；filter/must_not 只做是否匹配且结果可缓存（bitset）-> 精确过滤条件尽量放 filter");
        System.out.println("  term 查 keyword 字段不分词精确匹配；match 查 text 字段先分词。常见坑：对 text 字段用 term 查不到");
    }

    static void aggregation(List<Doc> data) {
        System.out.println("\n== 聚合 terms(category) + avg(price) ==");
        Map<String, List<Doc>> buckets = new TreeMap<>();
        for (Doc d : data) buckets.computeIfAbsent(d.category(), k -> new ArrayList<>()).add(d);
        buckets.forEach((k, v) -> System.out.printf("  %-7s doc_count=%d avg_price=%.1f%n", k, v.size(),
                v.stream().mapToInt(Doc::price).average().orElse(0)));
        System.out.println("  聚合依赖 doc_values（列式正排存储），text 字段默认不可聚合，需用 .keyword 子字段");
    }

    static void shardRouting() {
        System.out.println("\n== 分片路由: shard = hash(_routing) % number_of_primary_shards ==");
        int shards = 5;
        for (String id : List.of("1", "2", "3", "order-1001", "order-1002")) {
            System.out.printf("  doc %-10s -> shard %d%n", id, Math.floorMod(id.hashCode(), shards));
        }
        System.out.println("  因此主分片数创建后不可改（否则路由全乱），只能 reindex；副本分片数可随时改");
        System.out.println("  写：协调节点 -> 主分片 -> 同步到副本；读：协调节点在主/副本间轮询。近实时：refresh 默认 1s 后才可搜到；translog 保证不丢");
    }

    static void printSummary() {
        System.out.println("""

                核心概念对照：Index≈表、Document≈行、Field≈列、Mapping≈Schema、Shard=分片(Lucene 实例)、Replica=副本
                写入流程：内存 buffer + translog -> refresh(1s) 生成 segment 可搜索 -> flush 落盘清 translog -> segment merge
                深分页：from+size 要在协调节点合并 shards*(from+size) 条，超过 10000 禁止；用 search_after（游标）或 scroll（导出）
                与 MySQL 同步：canal 监听 binlog -> MQ -> 写 ES（最终一致）；或双写（易不一致）
                集群：master 节点（元数据）、data 节点、coordinating 节点；脑裂防护 discovery 的 quorum
                适用：全文检索、日志分析（ELK/EFK）、复杂多条件筛选、聚合统计；不适合：强事务、频繁更新、作为主存储
                客户端：Java API Client（8.x 官方）、Spring Data Elasticsearch、Easy-ES
                参考：elastic/elasticsearch、apache/lucene、medcl/elasticsearch-analysis-ik、《Elasticsearch 权威指南》
                """);
    }
}
