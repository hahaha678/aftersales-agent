package com.example.aftersales.aftersales.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record ConfirmReceiptDTO(
    @NotBlank
    @Size(max = 1000)
    @Schema(description = "实际收货核对记录，不代表已退款", example = "已核对退回商品与配件")
    String note
) {
    public ConfirmReceiptDTO {
        note = note == null ? null : note.strip();
    }
}
