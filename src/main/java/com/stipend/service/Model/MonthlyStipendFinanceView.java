package com.stipend.service.Model;

import com.stipend.service.Enum.StipendStatus;

import java.math.BigDecimal;
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
        int stipendYear,
        int stipendMonth,
        BigDecimal calculatedAmount,
        String currency,
        StipendStatus status
) {
    public LocalDate stipendPeriodStartDate() {
        return YearMonth.of(stipendYear, stipendMonth).atDay(1);
    }

    public LocalDate stipendPeriodEndDate() {
        return YearMonth.of(stipendYear, stipendMonth).atEndOfMonth();
    }
}
