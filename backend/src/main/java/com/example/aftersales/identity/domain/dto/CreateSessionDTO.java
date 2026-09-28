package com.example.aftersales.identity.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSessionDTO(
        @Schema(description = "账号，区分大小写；不在登录接口注册用户", example = "customer01")
        @NotBlank @Size(max = 64) String username,
        @Schema(description = "登录密码，不写入日志，不在响应中回显", format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 128) String password
) {}
