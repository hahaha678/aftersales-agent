package com.example.aftersales.aftersales.domain.po;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AftersalePO {

    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        id = value;
    }

    private Long userId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long value) {
        userId = value;
    }

    private Long orderId;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long value) {
        orderId = value;
    }

    private Long orderItemId;

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long value) {
        orderItemId = value;
    }

    private String requestKey;

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String value) {
        requestKey = value;
    }

    private Integer quantity;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer value) {
        quantity = value;
    }

    private BigDecimal amount;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal value) {
        amount = value;
    }

    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String value) {
        reason = value;
    }

    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        description = value;
    }

    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }

    private String ruleVersion;

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String value) {
        ruleVersion = value;
    }

    private LocalDateTime createdAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime value) {
        createdAt = value;
    }

    private LocalDateTime updatedAt;

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime value) {
        updatedAt = value;
    }

    private String orderNumber;

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String value) {
        orderNumber = value;
    }

    private String productName;

    public String getProductName() {
        return productName;
    }

    public void setProductName(String value) {
        productName = value;
    }

    private String specification;

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String value) {
        specification = value;
    }
}
