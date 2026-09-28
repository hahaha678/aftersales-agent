package com.example.aftersales.order.domain.vo;

import com.example.aftersales.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderDetailVO(
        @Schema(example = "2001") String id,
        @Schema(example = "ORD202609280001") String orderNumber,
        OrderStatus status,
        @Schema(description = "原始实付总额，人民币元", example = "199.00", pattern = "^\\d+\\.\\d{2}$") String paidAmount,
        @Schema(allowableValues = "CNY", example = "CNY") String currency,
        OffsetDateTime createdAt,
        @Schema(description = "未支付为 null", nullable = true) OffsetDateTime paidAt,
        @Schema(description = "未签收为 null；首版不支持一个订单多次拆分签收", nullable = true) OffsetDateTime signedAt,
        List<OrderItemVO> items
) {}
