package com.example.aftersales.order.domain.query;

import com.example.aftersales.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 查询条件只描述筛选与分页，当前用户身份由认证上下文提供。 */
public class OrderPageQuery {

    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    @Min(1)
    private Integer page = 1;

    @Schema(description = "每页条数，最多 100", defaultValue = "20")
    @Min(1)
    @Max(100)
    private Integer size = 20;

    @Schema(description = "可选状态，精确匹配")
    private OrderStatus status;

    @Schema(description = "可选订单号，精确匹配，不支持模糊搜索")
    @Size(max = 64)
    private String orderNumber;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page == null ? 1 : page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size == null ? 20 : size;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }
}
