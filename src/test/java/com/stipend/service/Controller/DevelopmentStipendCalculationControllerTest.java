package com.stipend.service.Controller;

import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.StipendRecords.MonthlyStipendService;
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

        var result = new DevelopmentStipendCalculationController(stipendService)
                .calculate(period);

        assertThat(result).containsExactly(
                new DevelopmentStipendCalculationController.CalculationResult(
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
}
