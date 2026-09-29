package com.example.aftersales.knowledge.domain.vo;

/** 引用定位到不可变文档版本和原文片段，score 是相似度而不是正确率。 */
public record PolicySourceVO(
    String sourceId,
    String policyId,
    String title,
    int version,
    String scope,
    String excerpt,
    String sourcePath,
    double score
) {}
