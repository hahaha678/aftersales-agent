package com.example.aftersales.aftersales.domain.dto;
import jakarta.validation.constraints.*;
public record ReviewAftersaleDTO(@NotNull Decision decision, @NotBlank @Size(max=1000) String note) {
 public enum Decision { APPROVED, REJECTED }
}
