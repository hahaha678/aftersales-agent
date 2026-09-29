package com.example.aftersales.aftersales.domain.vo;

import java.time.LocalDateTime;

/** 列表只返回元数据，图片通过单独的鉴权接口读取。 */
public record EvidenceVO(String id, String mediaType, int byteSize, LocalDateTime createdAt) {}
