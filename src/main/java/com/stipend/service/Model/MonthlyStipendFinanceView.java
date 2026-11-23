package com.stipend.service.Model;

import com.stipend.service.Enum.StipendStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public record MonthlyStipendFinanceView(
        UUID stipendId,
        UUID studentId,
        String firstName,
        String lastName,
        UUID cohortId,
        LocalDate cohortStartDate,
        LocalDate cohortEndDate,
        UUID policyId,
        int stipendYear,
        int stipendMonth,
        BigDecimal absenceCount,
        Integer scheduledTrainingDays,
        Integer eligibleTrainingDays,
        BigDecimal creditedAttendanceDays,
        BigDecimal attendancePercentage,
        BigDecimal unauthorizedAbsenceDays,
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
    public LocalDate stipendPeriodStartDate() {
        return YearMonth.of(stipendYear, stipendMonth).atDay(1);
    }

    public LocalDate stipendPeriodEndDate() {
        return YearMonth.of(stipendYear, stipendMonth).atEndOfMonth();
    }
}
