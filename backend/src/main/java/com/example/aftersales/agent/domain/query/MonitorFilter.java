package com.example.aftersales.agent.domain.query;

import java.time.LocalDateTime;

public record MonitorFilter(
    String status,
    String conversationId,
    LocalDateTime from,
    LocalDateTime until,
    int offset,
    int size
) {}
