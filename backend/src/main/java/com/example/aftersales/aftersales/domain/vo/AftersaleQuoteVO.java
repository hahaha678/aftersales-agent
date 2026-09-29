package com.example.aftersales.aftersales.domain.vo;

import java.math.BigDecimal;

public record AftersaleQuoteVO(
    BigDecimal amount,
    int availableQuantity,
    String productName,
    String orderNumber,
    String ruleVersion
) {}
