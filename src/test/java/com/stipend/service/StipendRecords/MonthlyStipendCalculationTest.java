package com.stipend.service.StipendRecords;

import com.stipend.service.Model.MonthlyStipend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyStipendCalculationTest {

    @Mock
    private MonthlyStipendService monthlyStipendService;

    private MonthlyStipendCalculation scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MonthlyStipendCalculation(monthlyStipendService, "Africa/Kigali");
    }

    @Test
    void scheduleMonthlyCalculation_withNullCutOffDate_throwsException() {
        assertThrows(NullPointerException.class, () -> scheduler.scheduleMonthlyCalculation((Date) null));
    }

    @Test
    void scheduleMonthlyCalculation_withCutOffDate_calculatesThatMonth() {
        Date cutoffDate = Date.from(LocalDate.of(2026, 2, 15).atStartOfDay(java.time.ZoneId.of("Africa/Kigali")).toInstant());
        YearMonth stipendMonth = YearMonth.of(2026, 2);

        when(monthlyStipendService.calculateMonthlyStipend(eq(stipendMonth))).thenReturn(List.of(new MonthlyStipend()));

        scheduler.scheduleMonthlyCalculation(cutoffDate);

        verify(monthlyStipendService).calculateMonthlyStipend(eq(stipendMonth));
    }

    @Test
    void scheduleMonthlyCalculation_withCutOffDate_rerunsExistingPeriod() {
        Date cutoffDate = Date.from(LocalDate.of(2026, 2, 15).atStartOfDay(java.time.ZoneId.of("Africa/Kigali")).toInstant());

        scheduler.scheduleMonthlyCalculation(cutoffDate);

        verify(monthlyStipendService).calculateMonthlyStipend(eq(YearMonth.of(2026, 2)));
    }

    @Test
    void scheduleMonthlyCalculation_noArg_invokesCalculationForCurrentMonth() {
        YearMonth currentMonth = YearMonth.from(
                java.time.ZonedDateTime.now(java.time.ZoneId.of("Africa/Kigali"))
        );

        scheduler.scheduleMonthlyCalculation();

        verify(monthlyStipendService).calculateMonthlyStipend(eq(currentMonth));
    }
}
