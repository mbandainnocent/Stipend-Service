package com.stipend.service.StipendRecords;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MonthlyStipendSchedulerTest {

    @Test
    void lastDayCronIsValid() {
        assertNotNull(CronExpression.parse("0 0 2 L * *"));
    }

    @Test
    void calculateMonthlyStipends_delegatesToCalculation() {
        MonthlyStipendCalculation calculation = mock(MonthlyStipendCalculation.class);
        MonthlyStipendScheduler scheduler = new MonthlyStipendScheduler(calculation);

        scheduler.calculateMonthlyStipends();

        verify(calculation).scheduleMonthlyCalculation();
    }
}
