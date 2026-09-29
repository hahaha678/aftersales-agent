package com.example.aftersales.aftersales.domain.po;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AftersaleDraftPO(
    String id,
    long userId,
    String conversationId,
    String runId,
    long orderId,
    long orderItemId,
    int quantity,
    String reason,
    String description,
    BigDecimal amount,
    int availableQuantity,
    String productName,
    String orderNumber,
    String ruleVersion,
    int version,
    String status,
    Long aftersaleId,
    LocalDateTime createdAt,
    LocalDateTime expiresAt
) {}
