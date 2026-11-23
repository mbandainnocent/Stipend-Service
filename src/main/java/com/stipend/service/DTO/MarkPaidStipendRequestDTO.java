package com.stipend.service.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record MarkPaidStipendRequestDTO(
        @NotBlank(message = "paymentReference must not be blank")
        @Size(max = 100, message = "paymentReference must not exceed 100 characters")
        String paymentReference,
        @NotNull(message = "paidAt is required")
        Instant paidAt
) {
}
