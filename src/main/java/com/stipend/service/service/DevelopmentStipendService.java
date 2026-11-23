package com.stipend.service.service;

import com.stipend.service.DTO.MonthlyStipendResponseDTO;
import com.stipend.service.StipendRecords.MonthlyStipendService;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DevelopmentStipendService {

    private final MonthlyStipendService monthlyStipendService;
    private final FinanceStipendReadService financeStipendReadService;

    public DevelopmentStipendService(
            MonthlyStipendService monthlyStipendService,
            FinanceStipendReadService financeStipendReadService
    ) {
        this.monthlyStipendService = monthlyStipendService;
        this.financeStipendReadService = financeStipendReadService;
    }

    public List<MonthlyStipendResponseDTO> calculateAndGetCurrentMonthStipends() {
        YearMonth period = YearMonth.now();
        monthlyStipendService.calculateMonthlyStipend(period);
        return financeStipendReadService.getStipendsForPeriod(period);
    }

    public Optional<MonthlyStipendResponseDTO> calculateAndGetCurrentMonthStipend(UUID studentId) {
        YearMonth period = YearMonth.now();
        monthlyStipendService.calculateMonthlyStipend(period);
        return financeStipendReadService.getStudentStipendForPeriod(studentId, period);
    }
}
