package com.example.aftersales.aftersales.domain.po;

/** 二进制内容只用于鉴权读取，不加入申请详情或列表查询。 */
public record EvidenceContentPO(byte[] content) {}
