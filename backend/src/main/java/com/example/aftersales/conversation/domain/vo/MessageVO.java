package com.example.aftersales.conversation.domain.vo;

import com.example.aftersales.conversation.domain.po.MessagePO;
import java.time.*;

public record MessageVO(String id, String runId, String role, String content, String status, OffsetDateTime createdAt) {
    public static MessageVO of(MessagePO row) {
        return new MessageVO(
            row.id(),
            row.runId(),
            row.role(),
            row.content(),
            row.status(),
            row.createdAt().atOffset(ZoneOffset.UTC)
        );
    }
}
