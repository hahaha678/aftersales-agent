package com.example.aftersales.aftersales.domain.vo;

import java.time.OffsetDateTime;

public record RefundVO(
    String requestKey,
    String operationNumber,
    String amount,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
