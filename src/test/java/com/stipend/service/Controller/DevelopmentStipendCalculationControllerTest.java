package com.stipend.service.Controller;

import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.DTO.CalculationResultDTO;
import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.StipendRecords.MonthlyStipendService;
import com.stipend.service.service.DevelopmentStipendService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevelopmentStipendCalculationControllerTest {

    @Test
    void calculatesPeriodAndReturnsUsefulResults() {
        MonthlyStipendService stipendService = mock(MonthlyStipendService.class);
        UUID studentId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        YearMonth period = YearMonth.of(2025, 1);
        when(stipendService.calculateMonthlyStipend(period)).thenReturn(List.of(
                MonthlyStipend.builder()
                        .studentId(studentId)
                        .cohortId(cohortId)
                        .stipendYear(2025)
                        .stipendMonth(1)
                        .calculatedAmount(new BigDecimal("20000.00"))
                        .currency("RWF")
                        .status(StipendStatus.CALCULATED)
                        .build()
        ));

        var result = new DevelopmentStipendCalculationController(stipendService, mock(DevelopmentStipendService.class))
                .calculate(period);

        assertThat(result).containsExactly(
                new CalculationResultDTO(
                        studentId,
                        cohortId,
                        2025,
                        1,
                        new BigDecimal("20000.00"),
                        "RWF",
                        StipendStatus.CALCULATED
                )
        );
        verify(stipendService).calculateMonthlyStipend(period);
    }

    @Test
    void getStipendsCalculatesAndReturnsFinanceRowsForCurrentMonth() {
        MonthlyStipendService stipendService = mock(MonthlyStipendService.class);
        DevelopmentStipendService developmentStipendService = mock(DevelopmentStipendService.class);
        YearMonth currentMonth = YearMonth.now();
        var response = List.of(
                new com.stipend.service.DTO.MonthlyStipendResponseDTO(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Aline",
                        "Uwase",
                        UUID.randomUUID(),
                        java.time.LocalDate.of(2026, 1, 1),
                        java.time.LocalDate.of(2026, 12, 31),
                        UUID.randomUUID(),
                        currentMonth.getYear(),
                        currentMonth.getMonthValue(),
                        currentMonth.atDay(1),
                        currentMonth.atEndOfMonth(),
                        BigDecimal.ZERO,
                        20,
                        20,
                        new BigDecimal("20.00"),
                        new BigDecimal("100.000000"),
                        BigDecimal.ZERO,
                        new BigDecimal("40000.00"),
                        new BigDecimal("100.00"),
                        new BigDecimal("40000.00"),
                        "RWF",
                        StipendStatus.CALCULATED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        java.time.Instant.now(),
                        java.time.Instant.now()
                )
        );
        when(developmentStipendService.calculateAndGetCurrentMonthStipends()).thenReturn(response);
        DevelopmentStipendCalculationController controller =
                new DevelopmentStipendCalculationController(stipendService, developmentStipendService);

        var result = controller.getStipends();

        assertThat(result.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
        verify(developmentStipendService).calculateAndGetCurrentMonthStipends();
    }
}
