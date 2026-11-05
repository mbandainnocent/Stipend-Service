package com.stipend.service.DTO;

import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Model.MonthlyStipend;

import java.math.BigDecimal;
import java.util.UUID;

public record CalculationResultDTO(
        UUID studentId,
        UUID cohortId,
        int stipendYear,
        int stipendMonth,
        BigDecimal calculatedAmount,
        String currency,
        StipendStatus status
) {
    public static CalculationResultDTO from(MonthlyStipend stipend) {
        return new CalculationResultDTO(
                stipend.getStudentId(),
                stipend.getCohortId(),
                stipend.getStipendYear(),
                stipend.getStipendMonth(),
                stipend.getCalculatedAmount(),
                stipend.getCurrency(),
                stipend.getStatus()
        );
    }
}
