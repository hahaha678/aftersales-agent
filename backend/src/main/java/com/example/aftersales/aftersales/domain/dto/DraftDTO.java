package com.example.aftersales.aftersales.domain.dto;

import com.example.aftersales.aftersales.domain.AftersaleReason;
import jakarta.validation.constraints.*;

public record DraftDTO(
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String orderId,
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String orderItemId,
    @Min(1) int quantity,
    @NotNull AftersaleReason reason,
    @NotBlank @Size(max = 1000) String description
) {}
