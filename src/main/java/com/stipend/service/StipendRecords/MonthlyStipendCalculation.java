package com.stipend.service.StipendRecords;

import com.stipend.service.Repository.StudentRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

@Component
public class MonthlyStipendCalculation {

    private static final Logger log = LoggerFactory.getLogger(MonthlyStipendCalculation.class);

    private final MonthlyStipendPeriodRunner periodRunner;
    private final StudentRegistrationRepository studentRepository;
    private final String businessTimeZone;

    public MonthlyStipendCalculation(
            MonthlyStipendPeriodRunner periodRunner,
            StudentRegistrationRepository studentRepository,
            @Value("${app.registration.business-time-zone:Africa/Kigali}") String businessTimeZone
    ) {
        this.periodRunner = periodRunner;
        this.studentRepository = studentRepository;
        this.businessTimeZone = businessTimeZone;
    }

    public void scheduleMonthlyCalculation() {
        calculateCompletedPeriods(LocalDate.now(ZoneId.of(businessTimeZone)));
    }

    void calculateCompletedPeriods(LocalDate today) {
        Objects.requireNonNull(today, "today must not be null");
        Optional<LocalDate> earliestCohortStart = studentRepository.findEarliestCohortStartDate();
        if (earliestCohortStart.isEmpty()) {
            log.info("No student cohorts are registered; skipping scheduled stipend calculations");
            return;
        }

        YearMonth earliestPeriod = YearMonth.from(earliestCohortStart.get());
        YearMonth latestCompletedPeriod = YearMonth.from(today.minusMonths(1));
        if (today.getDayOfMonth() == 1) {
            latestCompletedPeriod = latestCompletedPeriod.minusMonths(1);
        }

        if (earliestPeriod.isAfter(latestCompletedPeriod)) {
            log.info("No fully closed stipend periods are available to calculate");
            return;
        }

        for (YearMonth period = earliestPeriod;
             !period.isAfter(latestCompletedPeriod);
             period = period.plusMonths(1)) {
            boolean isMostRecentlyClosedPeriod = period.equals(latestCompletedPeriod);
            try {
                boolean calculated = periodRunner.calculateIfNeeded(
                        period,
                        today,
                        isMostRecentlyClosedPeriod
                );
                if (calculated) {
                    log.info("Scheduled stipend calculation recorded for period {}", period);
                }
            } catch (RuntimeException exception) {
                log.error("Scheduled stipend calculation failed for period {}", period, exception);
                throw exception;
            }
        }
    }

}
