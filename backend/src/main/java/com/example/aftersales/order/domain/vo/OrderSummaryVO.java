package com.example.aftersales.order.domain.vo;

import com.example.aftersales.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record OrderSummaryVO(
        @Schema(description = "订单 ID", example = "2001") String id,
        @Schema(description = "展示用订单号，不作为鉴权凭据", example = "ORD202609280001") String orderNumber,
        OrderStatus status,
        @Schema(description = "订单商品数量合计", example = "2") int totalQuantity,
        @Schema(description = "优惠分摊后的原始实付金额，元；售后退款不改写此值", example = "199.00", pattern = "^\\d+\\.\\d{2}$") String paidAmount,
        @Schema(allowableValues = "CNY", example = "CNY") String currency,
        @Schema(example = "2026-09-28T10:00:00+08:00") OffsetDateTime createdAt
) {}
