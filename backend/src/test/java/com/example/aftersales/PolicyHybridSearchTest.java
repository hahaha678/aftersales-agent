package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;

import com.example.aftersales.knowledge.domain.vo.PolicySourceVO;
import com.example.aftersales.knowledge.service.PolicyHybridSearch;
import java.util.*;
import org.junit.jupiter.api.Test;

class PolicyHybridSearchTest {

    private PolicySourceVO source(String id, String text, double score) {
        return new PolicySourceVO(id, id, "演示政策", 1, "MOUSE", text, "/policies/" + id, score);
    }

    @Test
    void recallsExplicitNegativePolicyBelowVectorThreshold() {
        var warranty = source("warranty", "此知识库没有统一保修年限或终身免费换新承诺。", 0.49);
        var materials = source("materials", "鼠标故障需填写表现与出现频率。", 0.3);
        var all = List.of(warranty, materials);
        assertThat(PolicyHybridSearch.select("鼠标是不是终身免费换新", all, 0.65, true)).containsExactly(warranty);
        assertThat(PolicyHybridSearch.select("鼠标是不是终身免费换新", all, 0.65, false)).isEmpty();
    }

    @Test
    void rejectsUnrelatedCommonAndSingleTermOverlap() {
        var all = List.of(source("warranty", "鼠标售后政策：保修期限以厂家凭证为准。", 0.4));
        for (String question : List.of("鼠标支持蓝牙吗", "今天天气怎么样", "鼠标政策", "保修", "   "))
            assertThat(PolicyHybridSearch.select(question, all, 0.65, true)).isEmpty();
    }

    @Test
    void retainsSemanticOnlyParaphraseAndDeduplicatesBothRoutes() {
        var semantic = source("semantic", "请描述间歇性的连接异常。", 0.8);
        var both = source("both", "断线故障需要描述频率。", 0.9);
        var results = PolicyHybridSearch.select("断线故障", List.of(semantic, both), 0.65, true);
        assertThat(results).containsExactly(both, semantic);
        assertThat(results.getFirst().score()).isEqualTo(0.9);
    }

    @Test
    void hasStableTopFourAndHandlesEnglishIdentifiers() {
        var all = new ArrayList<PolicySourceVO>();
        for (int i = 0; i < 8; i++) all.add(source("p" + i, "battery warranty DEVICE-123", 0.7));
        var ranked = PolicyHybridSearch.select("BATTERY warranty", all, 0.65, true);
        Collections.reverse(all);
        assertThat(PolicyHybridSearch.select("BATTERY warranty", all, 0.65, true))
            .isEqualTo(ranked)
            .hasSize(4);
        assertThat(PolicyHybridSearch.select("test", List.of(), 0.65, true)).isEmpty();
    }
}
