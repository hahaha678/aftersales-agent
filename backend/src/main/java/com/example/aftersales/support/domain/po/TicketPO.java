package com.example.aftersales.support.domain.po;

import java.time.LocalDateTime;

public record TicketPO(
    Long id,
    Long userId,
    String conversationId,
    String requestKey,
    String problem,
    Long orderId,
    Long aftersaleId,
    Long contextEndId,
    String status,
    Long assigneeId,
    String resolution,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
