package com.stipend.service.DTO;

import java.time.Instant;

public record MarkPaidStipendRequestDTO(String paymentReference, Instant paidAt) {
}
