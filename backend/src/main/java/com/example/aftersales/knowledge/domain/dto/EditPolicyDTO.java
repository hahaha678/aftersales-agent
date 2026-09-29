package com.example.aftersales.knowledge.domain.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** 指纹用于检测其他客服已保存的修改；政策标识、范围和版本号创建后保持不变。 */
public record EditPolicyDTO(
    @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String expectedFingerprint,
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 20000) String content,
    @NotNull LocalDate effectiveFrom,
    @NotNull LocalDate effectiveUntil
) {}
