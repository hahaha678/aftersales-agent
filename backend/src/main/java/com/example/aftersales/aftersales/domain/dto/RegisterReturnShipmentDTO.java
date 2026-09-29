package com.example.aftersales.aftersales.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record RegisterReturnShipmentDTO(
    @NotBlank
    @Pattern(regexp = "[\\p{L}\\p{N} ._-]{2,40}")
    @Schema(description = "退回承运商名称，2–40 字符", example = "顺丰速运")
    String carrier,
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]{5,63}")
    @Schema(description = "实际寄件单号，6–64 位字母、数字或连字符", example = "SF1234567890")
    String trackingNumber
) {
    public RegisterReturnShipmentDTO {
        carrier = carrier == null ? null : carrier.strip();
        trackingNumber = trackingNumber == null ? null : trackingNumber.strip();
    }
}
