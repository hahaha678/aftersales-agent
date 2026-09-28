package com.example.aftersales.identity.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserVO(
        @Schema(description = "用户 ID，字符串避免 JavaScript 精度丢失", example = "1001") String id,
        @Schema(example = "customer01") String username,
        @Schema(example = "演示用户") String displayName,
        @Schema(description = "当前角色，由后端决定，前端不可自行指定") Role role
) {
    public enum Role { CUSTOMER, STAFF }
}
