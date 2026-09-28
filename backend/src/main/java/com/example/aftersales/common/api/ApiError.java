package com.example.aftersales.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "统一错误体。HTTP 状态具有实际含义，不使用 HTTP 200 包装失败。")
public record ApiError(
        @Schema(example = "501") int status,
        @Schema(example = "NOT_IMPLEMENTED") String code,
        @Schema(example = "当前接口仅完成契约设计，业务尚未实现") String message,
        @Schema(example = "/api/orders") String path,
        @Schema(description = "服务端生成的请求追踪 ID，与 X-Request-Id 响应头一致") String requestId,
        @Schema(description = "字段错误；不包含密码或被拒绝的原始值") List<FieldViolation> errors
) {
    public record FieldViolation(String field, String message) {}
}
