package com.example.aftersales.aftersales.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SimulateRefundDTO(
    @NotNull @Schema(description = "模拟渠道结果，不产生真实资金变动") Mode mode,
    @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    @Schema(description = "首次为空；失败重试必须携带最近一笔失败流水的 requestKey")
    String previousKey
) {
    public enum Mode {
        SUCCESS,
        FAILURE,
        TIMEOUT_SUCCESS,
        TIMEOUT_FAILURE,
    }
}
