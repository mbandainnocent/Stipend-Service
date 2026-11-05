package com.stipend.service.StipendRecords;

import com.stipend.service.Repository.MonthlyStipendCalculationRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyStipendPeriodRunnerTest {

    @Mock
    private MonthlyStipendCalculationRunRepository runRepository;

    @Mock
    private MonthlyStipendService monthlyStipendService;

    @InjectMocks
    private MonthlyStipendPeriodRunner runner;

    @Test
    void calculateIfNeededClaimsRunsCalculatesAndCompletesAtomically() {
        YearMonth period = YearMonth.of(2026, 10);
        LocalDate runDate = LocalDate.of(2026, 11, 2);
        when(runRepository.existsByPeriodYearAndPeriodMonthAndRunDate(2026, 10, runDate))
                .thenReturn(false);
        when(runRepository.claimRun(any(UUID.class), anyInt(), anyInt(), any(), any()))
                .thenReturn(1);
        when(runRepository.completeRun(any(UUID.class), any())).thenReturn(1);

        assertTrue(runner.calculateIfNeeded(period, runDate, true));

        verify(monthlyStipendService).calculateMonthlyStipend(period);
        verify(runRepository).completeRun(any(UUID.class), any());
    }

    @Test
    void calculateIfNeededSkipsPeriodAlreadyCompleted() {
        YearMonth period = YearMonth.of(2026, 9);
        when(runRepository.existsByPeriodYearAndPeriodMonth(2026, 9)).thenReturn(true);

        assertFalse(runner.calculateIfNeeded(period, LocalDate.of(2026, 11, 2), false));

        verify(runRepository, never()).claimRun(any(), anyInt(), anyInt(), any(), any());
        verify(monthlyStipendService, never()).calculateMonthlyStipend(any());
    }

    @Test
    void calculateIfNeededSkipsWhenAnotherInstanceClaimsSameRunDate() {
        YearMonth period = YearMonth.of(2026, 10);
        LocalDate runDate = LocalDate.of(2026, 11, 2);
        when(runRepository.existsByPeriodYearAndPeriodMonthAndRunDate(2026, 10, runDate))
                .thenReturn(false);
        when(runRepository.claimRun(any(UUID.class), anyInt(), anyInt(), any(), any()))
                .thenReturn(0);

        assertFalse(runner.calculateIfNeeded(period, runDate, true));

        verify(monthlyStipendService, never()).calculateMonthlyStipend(any());
    }

    @Test
    void calculateIfNeededDoesNotCompleteRunWhenCalculationFails() {
        YearMonth period = YearMonth.of(2026, 10);
        LocalDate runDate = LocalDate.of(2026, 11, 2);
        when(runRepository.existsByPeriodYearAndPeriodMonthAndRunDate(2026, 10, runDate))
                .thenReturn(false);
        when(runRepository.claimRun(any(UUID.class), anyInt(), anyInt(), any(), any()))
                .thenReturn(1);
        doThrow(new IllegalStateException("calculation failed"))
                .when(monthlyStipendService).calculateMonthlyStipend(period);

        assertThrows(
                IllegalStateException.class,
                () -> runner.calculateIfNeeded(period, runDate, true)
        );

        verify(runRepository, never()).completeRun(any(), any());
    }
}
