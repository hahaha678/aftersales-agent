package com.example.aftersales.support.domain.dto;

import jakarta.validation.constraints.*;

public record CreateTicketDTO(
    @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String conversationId,
    @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String requestKey,
    @NotBlank @Size(max = 2000) String problem,
    @Pattern(regexp = "[1-9][0-9]{0,18}") String orderId,
    @Pattern(regexp = "[1-9][0-9]{0,18}") String aftersaleId
) {
    public CreateTicketDTO {
        if (problem != null) problem = problem.strip();
    }
}
