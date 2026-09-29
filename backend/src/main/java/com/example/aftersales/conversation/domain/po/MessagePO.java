package com.example.aftersales.conversation.domain.po;

import java.time.LocalDateTime;

public record MessagePO(String id, String runId, String role, String content, String status, LocalDateTime createdAt) {}
