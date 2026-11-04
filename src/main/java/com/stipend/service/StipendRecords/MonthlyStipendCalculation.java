package com.stipend.service.StipendRecords;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Objects;

@Component
public class MonthlyStipendCalculation {

    private static final Logger log = LoggerFactory.getLogger(MonthlyStipendCalculation.class);

    private final MonthlyStipendService monthlyStipendService;
    private final String businessTimeZone;

    public MonthlyStipendCalculation(
            MonthlyStipendService monthlyStipendService,
            @Value("${app.registration.business-time-zone:Africa/Kigali}") String businessTimeZone
    ) {
        this.monthlyStipendService = monthlyStipendService;
        this.businessTimeZone = businessTimeZone;
    }

    public void scheduleMonthlyCalculation() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(businessTimeZone));
        calculateForMonth(YearMonth.from(now));
    }

    public void scheduleMonthlyCalculation(Date cutOffDate) {
        Objects.requireNonNull(cutOffDate, "cutOffDate must not be null");

        LocalDate cutoffLocal = cutOffDate.toInstant()
                .atZone(ZoneId.of(businessTimeZone))
                .toLocalDate();

        log.info("Starting manual monthly stipend calculation with cutoff date: {}", cutoffLocal);

        calculateForMonth(YearMonth.from(cutoffLocal));
    }

    private void calculateForMonth(YearMonth period) {
        log.info("Starting monthly stipend calculation for period: {}", period);
        try {
            long startTime = System.currentTimeMillis();
            var stipends = monthlyStipendService.calculateMonthlyStipend(period);
            long duration = System.currentTimeMillis() - startTime;

            log.info(
                    "Monthly stipend calculation completed for period: {}. Records created/updated: {}. Duration: {}ms",
                    period,
                    stipends.size(),
                    duration
            );

            if (stipends.isEmpty()) {
                log.warn("No stipend records were calculated for period: {}. This may indicate no eligible students or no attendance data.", period);
            }
        } catch (Exception e) {
            log.error("Failed to calculate monthly stipends for period: {}", period, e);
            throw e;
        }
    }
}
