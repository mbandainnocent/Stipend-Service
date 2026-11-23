package com.stipend.service.DTO;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StipendPolicyResponseDTO(
        UUID id,
        UUID programId,
        UUID cohortId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean enabled,
        List<StipendTierResponseDTO> tiers,
        Instant createdAt,
        Instant updatedAt
) {
}
