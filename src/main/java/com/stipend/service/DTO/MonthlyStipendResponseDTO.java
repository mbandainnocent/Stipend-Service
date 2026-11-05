package com.stipend.service.DTO;

import com.stipend.service.Enum.StipendStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MonthlyStipendResponseDTO(
        UUID id,
        UUID studentId,
        String firstName,
        String lastName,
        UUID cohortId,
        LocalDate cohortStartDate,
        LocalDate cohortEndDate,
        UUID policyId,
        int stipendYear,
        int stipendMonth,
        LocalDate stipendPeriodStartDate,
        LocalDate stipendPeriodEndDate,
        int absenceCount,
        BigDecimal baseAmount,
        BigDecimal paymentPercentage,
        BigDecimal calculatedAmount,
        String currency,
        StipendStatus status,
        String approvedBy,
        String approvalComment,
        Instant approvedAt,
        String paymentReference,
        Instant paidAt,
        Instant calculatedAt,
        Instant updatedAt
) {
}
