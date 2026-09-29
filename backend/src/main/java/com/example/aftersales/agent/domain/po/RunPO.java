package com.example.aftersales.agent.domain.po;

import java.time.LocalDateTime;

/**
 * 一次用户提问对应一条任务记录，包含输入、最终回答、状态与模型报告的用量。
 * requestKey 用于发送幂等；expiresAt 是执行租约，服务中断后可据此回收遗留任务。
 * 这里的 LocalDateTime 按项目约定使用 UTC，转换给前端时再附加时区。
 */
public record RunPO(
    String id,
    String conversationId,
    long userId,
    String requestKey,
    String status,
    String userContent,
    String assistantContent,
    String errorMessage,
    String model,
    int inputTokens,
    int outputTokens,
    LocalDateTime createdAt,
    LocalDateTime finishedAt,
    LocalDateTime expiresAt
) {}
