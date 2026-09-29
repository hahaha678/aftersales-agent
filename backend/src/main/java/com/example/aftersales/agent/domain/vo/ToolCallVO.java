package com.example.aftersales.agent.domain.vo;

import java.time.OffsetDateTime;

public record ToolCallVO(
    String id,
    String toolName,
    String status,
    long durationMs,
    Integer callIndex,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    String inputSummary,
    String resultSummary,
    String errorCode
) {}
