package com.example.aftersales;

import static org.assertj.core.api.Assertions.*;

import com.example.aftersales.aftersales.domain.vo.DraftVO;
import com.example.aftersales.agent.service.DraftReplyGuard;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DraftReplyGuardTest {

    private String draft(String id, String product, String amount) {
        return JsonMapper.builder()
            .build()
            .writeValueAsString(
                new DraftVO(
                    id,
                    "conversation",
                    "1",
                    "2",
                    "DEMO-1001",
                    product,
                    2,
                    "DAMAGED",
                    "运输破损",
                    amount,
                    1,
                    "READY",
                    null,
                    "RETURN_7D_V1",
                    OffsetDateTime.now().plusMinutes(15)
                )
            );
    }

    @Test
    void blocksInventedSuccessWithoutAWriteResult() {
        var guard = new DraftReplyGuard();
        assertThat(guard.finish("已为你生成退货退款申请草稿：鼠标垫，70元。请到确认卡片提交。"))
            .contains("没有成功生成")
            .doesNotContain("70元");
        guard.record("createAftersaleDraft", "{\"error\":\"STATE_CONFLICT\",\"message\":\"超期\"}");
        assertThat(guard.finish("已生成草稿")).contains("没有成功生成");
        assertThat(guard.finish("请补充申请数量和原因，我才能生成草稿。")).startsWith("请补充");
    }

    @Test
    void onlySuccessfulCurrentWriteResultsCanProduceConfirmation() {
        var guard = new DraftReplyGuard();
        guard.record("getMyOrder", draft("old", "旧商品", "129.00"));
        assertThat(guard.finish("已为你生成草稿")).contains("没有成功生成");
        guard.record("createAftersaleDraft", draft("new", "鼠标垫", "70.00"));
        var answer = guard.finish("无线鼠标申请129元，已提交。");
        assertThat(answer).contains("鼠标垫", "70.00", "尚未提交").doesNotContain("129元", "无线鼠标");
        assertThat(new DraftReplyGuard().finish("已生成第二份草稿")).contains("没有成功生成");
    }

    @Test
    void includesAllSuccessfulDraftsWithoutDuplicateCardsInSummary() {
        var guard = new DraftReplyGuard();
        guard.record("createAftersaleDraft", draft("one", "无线鼠标", "129.00"));
        guard.record("createAftersaleDraft", draft("two", "鼠标垫", "70.00"));
        guard.record("createAftersaleDraft", draft("two", "鼠标垫", "70.00"));
        assertThat(guard.finish("")).contains("无线鼠标", "鼠标垫", "每份草稿需要单独确认");
        assertThat(guard.finish("").split("商品：", -1)).hasSize(3);
    }
}
