package com.stipend.service.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "monthly_stipend_calculation_runs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stipend_calculation_period_run_date",
                columnNames = {"period_year", "period_month", "run_date"}
        )
)
public class MonthlyStipendCalculationRun {

    @Id
    private UUID id;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected MonthlyStipendCalculationRun() {
    }

    public MonthlyStipendCalculationRun(
            UUID id,
            int periodYear,
            int periodMonth,
            LocalDate runDate,
            Instant startedAt
    ) {
        this.id = id;
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
        this.runDate = runDate;
        this.startedAt = startedAt;
    }
}
