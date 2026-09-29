package com.example.aftersales.agent.domain.po;

import java.time.LocalDateTime;

public record ToolCallPO(
    Long id,
    String toolName,
    String status,
    Long durationMs,
    Integer callIndex,
    LocalDateTime startedAt,
    LocalDateTime createdAt,
    String inputSummary,
    String resultSummary,
    String errorCode
) {}
