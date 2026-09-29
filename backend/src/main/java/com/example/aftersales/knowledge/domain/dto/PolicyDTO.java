package com.example.aftersales.knowledge.domain.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** 未发布草稿可编辑；已发布政策的新版本作为新草稿录入，避免覆盖已有引用原文。 */
public record PolicyDTO(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String policyKey,
    @Min(1) int version,
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String scope,
    @NotBlank @Size(max = 20000) String content,
    @NotNull LocalDate effectiveFrom,
    @NotNull LocalDate effectiveUntil
) {}
