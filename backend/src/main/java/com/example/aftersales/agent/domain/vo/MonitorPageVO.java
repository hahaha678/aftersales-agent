package com.example.aftersales.agent.domain.vo;

import java.util.List;

public record MonitorPageVO(List<MonitorRunVO> items, int page, int size, long total) {}
