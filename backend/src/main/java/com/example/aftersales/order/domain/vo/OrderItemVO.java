package com.example.aftersales.order.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

public record OrderItemVO(
        @Schema(description = "订单商品项 ID，后续售后申请引用此 ID", example = "3001") String id,
        @Schema(description = "商品 SKU 标识", example = "4001") String skuId,
        @Schema(description = "下单时商品名称快照", example = "无线键盘") String productName,
        @Schema(description = "下单时规格快照", example = "白色 / 标准版") String specification,
        @Schema(example = "2") int quantity,
        @Schema(description = "该商品项整行实付总额，含全部数量，不是单价；人民币元", example = "199.00", pattern = "^\\d+\\.\\d{2}$") String paidAmount,
        @Schema(description = "数量层面剩余可申请值；当前尚无售后申请，等于购买数量。接入售后后扣除占用及已处理数量，不代表满足期限等资格", example = "2") int availableAftersalesQuantity
) {}
