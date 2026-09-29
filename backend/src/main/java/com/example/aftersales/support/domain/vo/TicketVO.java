package com.example.aftersales.support.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record TicketVO(
    String id,
    String conversationId,
    String problem,
    String orderId,
    String aftersaleId,
    String status,
    String assigneeId,
    String resolution,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<Event> events
) {
    public record Event(String action, String note, OffsetDateTime occurredAt) {}
}
