package com.example.aftersales.support.domain.po;

import java.time.LocalDateTime;

public record TicketEventPO(String action, String note, LocalDateTime occurredAt) {}
