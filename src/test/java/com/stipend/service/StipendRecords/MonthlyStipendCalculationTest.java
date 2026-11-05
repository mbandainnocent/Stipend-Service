package com.stipend.service.StipendRecords;

import com.stipend.service.Repository.StudentRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyStipendCalculationTest {

    @Mock
    private MonthlyStipendPeriodRunner periodRunner;

    @Mock
    private StudentRegistrationRepository studentRepository;

    private MonthlyStipendCalculation scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MonthlyStipendCalculation(
                periodRunner,
                studentRepository,
                "Africa/Kigali"
        );
    }

    @Test
    void scheduleCalculations_waitsUntilSecondDayAndProcessesMostRecentlyClosedMonth() {
        when(studentRepository.findEarliestCohortStartDate())
                .thenReturn(java.util.Optional.of(LocalDate.of(2026, 8, 15)));

        scheduler.calculateCompletedPeriods(LocalDate.of(2026, 11, 1));
        verify(periodRunner).calculateIfNeeded(
                eq(YearMonth.of(2026, 8)),
                eq(LocalDate.of(2026, 11, 1)),
                eq(false)
        );
        verify(periodRunner).calculateIfNeeded(
                eq(YearMonth.of(2026, 9)),
                eq(LocalDate.of(2026, 11, 1)),
                eq(true)
        );
        verify(periodRunner, org.mockito.Mockito.never()).calculateIfNeeded(
                eq(YearMonth.of(2026, 10)),
                any(),
                anyBoolean()
        );

        scheduler.calculateCompletedPeriods(LocalDate.of(2026, 11, 2));
        verify(periodRunner).calculateIfNeeded(
                eq(YearMonth.of(2026, 10)),
                eq(LocalDate.of(2026, 11, 2)),
                eq(true)
        );
    }

    @Test
    void scheduleCalculationsStartsFromEarliestCohortAndRerunsLatestClosedPeriodDaily() {
        when(studentRepository.findEarliestCohortStartDate())
                .thenReturn(java.util.Optional.of(LocalDate.of(2026, 9, 15)));
        when(periodRunner.calculateIfNeeded(any(), any(), anyBoolean())).thenReturn(false);

        scheduler.calculateCompletedPeriods(LocalDate.of(2026, 11, 3));

        verify(periodRunner).calculateIfNeeded(
                YearMonth.of(2026, 9),
                LocalDate.of(2026, 11, 3),
                false
        );
        verify(periodRunner).calculateIfNeeded(
                YearMonth.of(2026, 10),
                LocalDate.of(2026, 11, 3),
                true
        );
    }

    @Test
    void scheduleCalculationsWhenNoStudentsExistDoesNothing() {
        when(studentRepository.findEarliestCohortStartDate()).thenReturn(java.util.Optional.empty());

        scheduler.calculateCompletedPeriods(LocalDate.of(2026, 11, 3));

        org.mockito.Mockito.verifyNoInteractions(periodRunner);
    }
}
