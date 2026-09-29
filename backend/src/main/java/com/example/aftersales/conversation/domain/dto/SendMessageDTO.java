package com.example.aftersales.conversation.domain.dto;

import jakarta.validation.constraints.*;

public record SendMessageDTO(
    @NotBlank @Size(max = 2000) String content,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{16,64}") String requestKey
) {}
