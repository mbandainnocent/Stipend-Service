package com.stipend.service.DTO;

import java.math.BigDecimal;
import java.util.UUID;

public record StipendTierResponseDTO(
        UUID id,
        BigDecimal minimumAttendancePercentage,
        BigDecimal paymentPercentage
) {
}
