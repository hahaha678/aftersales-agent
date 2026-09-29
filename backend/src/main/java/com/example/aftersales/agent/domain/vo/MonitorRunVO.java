package com.example.aftersales.agent.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record MonitorRunVO(
    String id,
    String conversationId,
    String status,
    String storedStatus,
    String model,
    int inputTokens,
    int outputTokens,
    long durationMs,
    OffsetDateTime createdAt,
    OffsetDateTime finishedAt,
    long toolCalls,
    long failedToolCalls,
    String errorCode,
    boolean ownConversation,
    String userContent,
    String assistantContent,
    List<String> ticketIds
) {}
