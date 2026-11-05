package com.stipend.service.service;

import com.stipend.service.StipendRecords.MonthlyStipendService;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevelopmentStipendServiceTest {

    @Test
    void calculatesCurrentPeriodBeforeReturningStipends() {
        MonthlyStipendService monthlyStipendService = mock(MonthlyStipendService.class);
        FinanceStipendReadService financeStipendReadService = mock(FinanceStipendReadService.class);
        DevelopmentStipendService service =
                new DevelopmentStipendService(monthlyStipendService, financeStipendReadService);
        YearMonth currentMonth = YearMonth.now();
        var expected = List.<com.stipend.service.DTO.MonthlyStipendResponseDTO>of();
        when(financeStipendReadService.getStipendsForPeriod(currentMonth)).thenReturn(expected);

        assertThat(service.calculateAndGetCurrentMonthStipends()).isSameAs(expected);

        verify(monthlyStipendService).calculateMonthlyStipend(currentMonth);
        verify(financeStipendReadService).getStipendsForPeriod(currentMonth);
    }

    @Test
    void calculatesCurrentPeriodBeforeLookingUpStudentStipend() {
        MonthlyStipendService monthlyStipendService = mock(MonthlyStipendService.class);
        FinanceStipendReadService financeStipendReadService = mock(FinanceStipendReadService.class);
        DevelopmentStipendService service =
                new DevelopmentStipendService(monthlyStipendService, financeStipendReadService);
        YearMonth currentMonth = YearMonth.now();
        UUID studentId = UUID.randomUUID();
        when(financeStipendReadService.getStudentStipendForPeriod(studentId, currentMonth))
                .thenReturn(java.util.Optional.empty());

        assertThat(service.calculateAndGetCurrentMonthStipend(studentId)).isEmpty();

        verify(monthlyStipendService).calculateMonthlyStipend(currentMonth);
        verify(financeStipendReadService).getStudentStipendForPeriod(studentId, currentMonth);
    }
}
