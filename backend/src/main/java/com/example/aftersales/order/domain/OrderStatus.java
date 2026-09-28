package com.example.aftersales.order.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "订单状态：PENDING_PAYMENT 待付款，PAID 已付款待发货，SHIPPED 已发货，COMPLETED 已完成，CANCELLED 已取消。售后状态单独维护。")
public enum OrderStatus { PENDING_PAYMENT, PAID, SHIPPED, COMPLETED, CANCELLED }
