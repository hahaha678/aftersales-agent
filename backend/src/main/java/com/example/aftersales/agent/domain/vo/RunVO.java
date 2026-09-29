package com.example.aftersales.agent.domain.vo;

import com.example.aftersales.agent.domain.po.RunPO;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 对外展示的任务结果，省略用户 ID、请求幂等键和内部租约等持久化字段。 */
public record RunVO(
    String id,
    String conversationId,
    String status,
    String content,
    String errorMessage,
    String model,
    int inputTokens,
    int outputTokens,
    OffsetDateTime createdAt
) {
    // 显式标注 UTC，避免客户端把无时区的数据库时间误当成本地时间。
    public static RunVO of(RunPO row) {
        return new RunVO(
            row.id(),
            row.conversationId(),
            row.status(),
            row.assistantContent(),
            row.errorMessage(),
            row.model(),
            row.inputTokens(),
            row.outputTokens(),
            row.createdAt().atOffset(ZoneOffset.UTC)
        );
    }
}
