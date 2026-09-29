package com.example.aftersales.support.domain.dto;

import jakarta.validation.constraints.*;

public record ResolveTicketDTO(@NotBlank @Size(max = 2000) String resolution) {
    public ResolveTicketDTO {
        if (resolution != null) resolution = resolution.strip();
    }
}
