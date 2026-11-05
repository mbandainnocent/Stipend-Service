package com.stipend.service.StipendRecords;

import com.stipend.service.Repository.MonthlyStipendCalculationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Service
public class MonthlyStipendPeriodRunner {

    private static final Logger log = LoggerFactory.getLogger(MonthlyStipendPeriodRunner.class);

    private final MonthlyStipendCalculationRunRepository runRepository;
    private final MonthlyStipendService monthlyStipendService;

    public MonthlyStipendPeriodRunner(
            MonthlyStipendCalculationRunRepository runRepository,
            MonthlyStipendService monthlyStipendService
    ) {
        this.runRepository = runRepository;
        this.monthlyStipendService = monthlyStipendService;
    }

    @Transactional
    public boolean calculateIfNeeded(
            YearMonth period,
            LocalDate runDate,
            boolean recalculateForRunDate
    ) {
        if (recalculateForRunDate) {
            if (runRepository.existsByPeriodYearAndPeriodMonthAndRunDate(
                    period.getYear(),
                    period.getMonthValue(),
                    runDate
            )) {
                return false;
            }
        } else if (runRepository.existsByPeriodYearAndPeriodMonth(
                period.getYear(),
                period.getMonthValue()
        )) {
            return false;
        }

        UUID runId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        int claimed = runRepository.claimRun(
                runId,
                period.getYear(),
                period.getMonthValue(),
                runDate,
                startedAt
        );
        if (claimed == 0) {
            return false;
        }

        log.info("Starting scheduled stipend calculation for period {}", period);
        long startTime = System.currentTimeMillis();
        var stipends = monthlyStipendService.calculateMonthlyStipend(period);
        int completed = runRepository.completeRun(runId, Instant.now());
        if (completed != 1) {
            throw new IllegalStateException("Could not mark stipend calculation run complete: " + runId);
        }
        log.info(
                "Completed scheduled stipend calculation for period {}: {} records in {}ms",
                period,
                stipends.size(),
                System.currentTimeMillis() - startTime
        );
        if (stipends.isEmpty()) {
            log.warn("No stipend records were calculated for period {}", period);
        }
        return true;
    }
}
