package com.example.aftersales.conversation.domain.dto;

import jakarta.validation.constraints.*;

public record ConversationDTO(@NotBlank @Size(max = 100) String title) {}
