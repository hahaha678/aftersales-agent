package com.example.aftersales.conversation.domain.po;

import java.time.LocalDateTime;

public record ConversationPO(String id, long userId, String title, LocalDateTime createdAt) {}
