package com.example.aftersales.aftersales.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record AftersaleVO(
    String id,
    String orderId,
    String orderItemId,
    String orderNumber,
    String productName,
    String specification,
    int quantity,
    String amount,
    String currency,
    String type,
    String reason,
    String description,
    String status,
    String ruleVersion,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<Event> events,
    ReturnShipment returnShipment,
    Receipt receipt,
    List<RefundVO> refunds
) {
    public record Event(String action, String note, OffsetDateTime occurredAt) {}

    public record ReturnShipment(String carrier, String trackingNumber, OffsetDateTime registeredAt) {}

    public record Receipt(String note, OffsetDateTime receivedAt) {}
}
