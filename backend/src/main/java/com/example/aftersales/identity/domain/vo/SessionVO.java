package com.example.aftersales.identity.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record SessionVO(
        @Schema(description = "登录成功后一次性返回的会话凭据，后续作为 Bearer token 发送") String accessToken,
        @Schema(allowableValues = "Bearer", example = "Bearer") String tokenType,
        @Schema(description = "令牌绝对过期时间", example = "2026-09-28T18:00:00+08:00") OffsetDateTime expiresAt,
        UserVO user
) {}
