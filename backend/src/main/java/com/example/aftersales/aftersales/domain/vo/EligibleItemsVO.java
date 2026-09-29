package com.example.aftersales.aftersales.domain.vo;

import java.util.List;

/** 按可申请商品项分页，同一订单的不同商品可能出现在不同页。金额为剩余总额。 */
public record EligibleItemsVO(List<Item> items, int page, int size, boolean hasMore, Integer nextPage) {
    public record Item(
        String orderId,
        String orderNumber,
        String orderItemId,
        String productName,
        int availableQuantity,
        String remainingAmount
    ) {}
}
