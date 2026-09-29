package com.example.aftersales.knowledge.service;

import com.example.aftersales.knowledge.domain.vo.PolicySourceVO;
import java.util.*;
import java.util.regex.Pattern;

/** 小规模语料的 BM25 + 向量双路召回。调用方必须先完成范围、发布状态和生效日期过滤。 */
public final class PolicyHybridSearch {

    private PolicyHybridSearch() {}

    // 中文按相邻双字切分，无需引入分词服务；英文/数字保留完整词。不是语义分词器。
    private static final Pattern WORDS = Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+(?:[-_][a-z0-9]+)*");
    private static final Set<String> STOP = Set.of(
        "鼠标",
        "商品",
        "政策",
        "售后",
        "申请",
        "订单",
        "项目",
        "演示",
        "问题",
        "相关",
        "说明",
        "什么",
        "哪些",
        "怎么",
        "是否",
        "是不是",
        "可以",
        "需要",
        "我的",
        "没有",
        "不能",
        "这个",
        "我们",
        "你们"
    );

    static List<String> tokens(String text) {
        var result = new ArrayList<String>();
        var matcher = WORDS.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (Character.UnicodeScript.of(word.codePointAt(0)) == Character.UnicodeScript.HAN) {
                int[] points = word.codePoints().toArray();
                for (int i = 0; i + 1 < points.length; i++) {
                    String token = new String(points, i, 2);
                    if (!STOP.contains(token)) result.add(token);
                }
            } else if (word.length() > 1 && !STOP.contains(word)) result.add(word);
        }
        return result;
    }

    public static List<PolicySourceVO> select(
        String question,
        List<PolicySourceVO> candidates,
        double minScore,
        boolean hybrid
    ) {
        if (candidates.isEmpty()) return List.of();
        var semantic = candidates
            .stream()
            .filter(c -> c.score() >= minScore)
            .sorted(
                Comparator.comparingDouble(PolicySourceVO::score).reversed().thenComparing(PolicySourceVO::sourceId)
            )
            .limit(20)
            .toList();
        if (!hybrid) return semantic.stream().limit(4).toList();

        var terms = new LinkedHashSet<>(tokens(question));
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        Map<String, Integer> frequencies = new HashMap<>();
        Map<String, Integer> lengths = new HashMap<>();
        for (var candidate : candidates) {
            Map<String, Integer> tf = new HashMap<>();
            for (String token : tokens(candidate.title() + " " + candidate.excerpt())) tf.merge(token, 1, Integer::sum);
            counts.put(candidate.sourceId(), tf);
            lengths.put(candidate.sourceId(), tf.values().stream().mapToInt(Integer::intValue).sum());
            for (String term : terms) if (tf.containsKey(term)) frequencies.merge(term, 1, Integer::sum);
        }
        double average = Math.max(1, lengths.values().stream().mapToInt(Integer::intValue).average().orElse(1));
        Map<String, Double> lexicalScores = new HashMap<>();
        for (var candidate : candidates) {
            var tf = counts.get(candidate.sourceId());
            int matches = 0;
            double score = 0;
            for (String term : terms) {
                int frequency = tf.getOrDefault(term, 0);
                if (frequency == 0) continue;
                matches++;
                int df = frequencies.get(term);
                double idf = Math.log(1 + (candidates.size() - df + 0.5) / (df + 0.5));
                score +=
                    (idf * frequency * 2.2) /
                    (frequency + 1.2 * (0.25 + (0.75 * lengths.get(candidate.sourceId())) / average));
            }
            // 单个通用词不足以让低向量分片段入选；至少两个不同有效词面证据。
            if (matches >= 2) lexicalScores.put(candidate.sourceId(), score);
        }
        var lexical = candidates
            .stream()
            .filter(c -> lexicalScores.containsKey(c.sourceId()))
            .sorted(
                Comparator.<PolicySourceVO>comparingDouble(c -> lexicalScores.get(c.sourceId()))
                    .reversed()
                    .thenComparing(PolicySourceVO::sourceId)
            )
            .limit(20)
            .toList();
        // RRF 只融合名次，避免把 BM25 与余弦相似度直接相加；同一片段只返回一次。
        Map<String, Double> fused = new HashMap<>();
        for (var route : List.of(semantic, lexical))
            for (int i = 0; i < route.size(); i++) fused.merge(
                route.get(i).sourceId(),
                1.0 / (60 + i + 1),
                Double::sum
            );
        return candidates
            .stream()
            .filter(c -> fused.containsKey(c.sourceId()))
            .sorted(
                Comparator.<PolicySourceVO>comparingDouble(c -> fused.get(c.sourceId()))
                    .reversed()
                    .thenComparing(Comparator.comparingDouble(PolicySourceVO::score).reversed())
                    .thenComparing(PolicySourceVO::sourceId)
            )
            .limit(4)
            .toList();
        // 对外 score 仍是向量相似度，不是融合分数，更不是答案正确率。
    }
}
