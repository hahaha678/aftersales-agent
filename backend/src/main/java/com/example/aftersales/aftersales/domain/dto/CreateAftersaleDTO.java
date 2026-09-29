package com.example.aftersales.aftersales.domain.dto;

import com.example.aftersales.aftersales.domain.AftersaleReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record CreateAftersaleDTO(
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String orderId,
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String orderItemId,
    @Min(1) int quantity,
    @NotNull AftersaleReason reason,
    @NotBlank @Size(max = 1000) String description,
    @Schema(
        description = "客户端生成的唯一请求键；同一提交重试必须复用，不同内容不可复用",
        example = "550e8400-e29b-41d4-a716-446655440000"
    )
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{16,64}")
    String requestKey
) {}
