package com.example.aftersales.aftersales.domain.dto;

import jakarta.validation.constraints.*;

public record ConfirmDraftDTO(@Min(1) int version, @AssertTrue boolean confirmed) {}
