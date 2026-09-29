package com.example.aftersales.aftersales.domain.vo;

import java.util.List;

public record AftersalePageVO(List<AftersaleVO> items, int page, int size, long total) {}
