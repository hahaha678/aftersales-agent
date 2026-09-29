package com.example.aftersales.knowledge.domain.query;

import jakarta.validation.constraints.*;

/** scope 为受控适用范围，例如 MOUSE；通用政策使用 GLOBAL。 */
public record PolicySearchQuery(
    @NotBlank @Size(max = 500) String question,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String scope
) {}
