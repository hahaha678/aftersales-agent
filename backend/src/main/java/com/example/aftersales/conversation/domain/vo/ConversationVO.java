package com.example.aftersales.conversation.domain.vo;

import com.example.aftersales.conversation.domain.po.ConversationPO;
import java.time.*;

public record ConversationVO(String id, String title, OffsetDateTime createdAt) {
    public static ConversationVO of(ConversationPO row) {
        return new ConversationVO(row.id(), row.title(), row.createdAt().atOffset(ZoneOffset.UTC));
    }
}
