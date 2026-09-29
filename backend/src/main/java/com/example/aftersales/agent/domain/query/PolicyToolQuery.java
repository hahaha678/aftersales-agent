package com.example.aftersales.agent.domain.query;

import jakarta.validation.constraints.*;

/** 改写只能保留原问题的政策意图，不能改变商品范围或加入预期答案。 */
public record PolicyToolQuery(
    @NotBlank @Size(max = 500) String question,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String scope,
    @Size(max = 500) String fallbackQuestion
) {}
