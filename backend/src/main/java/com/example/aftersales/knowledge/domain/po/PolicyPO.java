package com.example.aftersales.knowledge.domain.po;

import java.time.LocalDate;

public record PolicyPO(
    String id,
    String policyKey,
    int version,
    String title,
    String scope,
    String content,
    String status,
    LocalDate effectiveFrom,
    LocalDate effectiveUntil,
    String embeddingIdentity
) {}
