package com.stipend.service.StipendRecords;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonthlyStipendScheduler {

    private final MonthlyStipendCalculation calculation;

    public MonthlyStipendScheduler(MonthlyStipendCalculation calculation) {
        this.calculation = calculation;
    }

    @Scheduled(
            cron = "${app.stipend.calculation.cron:0 0 2 * * *}",
            zone = "${app.registration.business-time-zone:Africa/Kigali}"
    )
    public void calculateMonthlyStipends() {
        calculation.scheduleMonthlyCalculation();
    }
}
