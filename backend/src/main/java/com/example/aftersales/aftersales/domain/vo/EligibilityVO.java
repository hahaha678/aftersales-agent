package com.example.aftersales.aftersales.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record EligibilityVO(String orderId, String ruleVersion, OffsetDateTime deadline, List<Item> items) {
    public record Item(
        String orderItemId,
        String productName,
        int availableQuantity,
        String remainingAmount,
        boolean eligible,
        String reason
    ) {}
}
