package com.stipend.service.Repository;

import com.stipend.service.Model.MonthlyStipendCalculationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface MonthlyStipendCalculationRunRepository
        extends JpaRepository<MonthlyStipendCalculationRun, UUID> {

    boolean existsByPeriodYearAndPeriodMonth(int periodYear, int periodMonth);

    boolean existsByPeriodYearAndPeriodMonthAndRunDate(
            int periodYear,
            int periodMonth,
            LocalDate runDate
    );

    @Modifying
    @Query(value = """
            insert into monthly_stipend_calculation_runs
                (id, period_year, period_month, run_date, started_at)
            values
                (:id, :year, :month, :runDate, :startedAt)
            on conflict (period_year, period_month, run_date) do nothing
            """, nativeQuery = true)
    int claimRun(
            @Param("id") UUID id,
            @Param("year") int year,
            @Param("month") int month,
            @Param("runDate") LocalDate runDate,
            @Param("startedAt") Instant startedAt
    );

    @Modifying
    @Query(value = """
            update monthly_stipend_calculation_runs
            set completed_at = :completedAt
            where id = :id
            """, nativeQuery = true)
    int completeRun(@Param("id") UUID id, @Param("completedAt") Instant completedAt);
}
