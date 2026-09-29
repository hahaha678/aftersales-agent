package com.example.aftersales.order.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record OrderPageVO(
    List<OrderSummaryVO> items,
    @Schema(description = "页码，从 1 开始", example = "1") int page,
    @Schema(description = "每页数量，1~100", example = "20") int size,
    @Schema(description = "匹配订单总条数", example = "1") long total
) {}
