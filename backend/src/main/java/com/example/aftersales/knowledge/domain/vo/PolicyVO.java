package com.example.aftersales.knowledge.domain.vo;

import com.example.aftersales.knowledge.domain.po.PolicyPO;
import java.time.LocalDate;

public record PolicyVO(
    String id,
    String policyKey,
    int version,
    String title,
    String scope,
    String content,
    String status,
    LocalDate effectiveFrom,
    LocalDate effectiveUntil,
    String fingerprint
) {
    public static PolicyVO of(PolicyPO row) {
        return new PolicyVO(
            row.id(),
            row.policyKey(),
            row.version(),
            row.title(),
            row.scope(),
            row.content(),
            row.status(),
            row.effectiveFrom(),
            row.effectiveUntil(),
            com.example.aftersales.knowledge.service.PolicyFingerprint.of(row)
        );
    }
}
