package com.example.aftersales.aftersales.domain.po;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundPO(
    Long id,
    Long requestId,
    String requestKey,
    String previousKey,
    String operationNumber,
    BigDecimal amount,
    String mode,
    String status,
    Long actorId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
