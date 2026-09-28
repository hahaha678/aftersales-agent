package com.example.aftersales.aftersales.domain.po;
import java.time.LocalDateTime;
public record AftersaleEventPO(String action, String note, LocalDateTime occurredAt) {}
