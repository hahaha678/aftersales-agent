package com.example.aftersales.aftersales.domain.vo;

import com.example.aftersales.aftersales.domain.po.AftersaleDraftPO;
import java.time.*;

public record DraftVO(
    String id,
    String conversationId,
    String orderId,
    String orderItemId,
    String orderNumber,
    String productName,
    int quantity,
    String reason,
    String description,
    String amount,
    int version,
    String status,
    String aftersaleId,
    String ruleVersion,
    OffsetDateTime expiresAt
) {
    public static DraftVO of(AftersaleDraftPO row) {
        return new DraftVO(
            row.id(),
            row.conversationId(),
            "" + row.orderId(),
            "" + row.orderItemId(),
            row.orderNumber(),
            row.productName(),
            row.quantity(),
            row.reason(),
            row.description(),
            row.amount().toPlainString(),
            row.version(),
            row.status().equals("READY") && !row.expiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))
                ? "EXPIRED"
                : row.status(),
            row.aftersaleId() == null ? null : row.aftersaleId().toString(),
            row.ruleVersion(),
            row.expiresAt().atOffset(ZoneOffset.UTC)
        );
    }
}
