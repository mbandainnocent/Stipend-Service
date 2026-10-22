package com.stipend.service.Controller;

import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Model.MonthlyStipend;
import com.stipend.service.StipendRecords.MonthlyStipendService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@Profile("dev")
@RequestMapping("/api/dev/stipends")
public class DevelopmentStipendCalculationController {

    private final MonthlyStipendService monthlyStipendService;

    public DevelopmentStipendCalculationController(MonthlyStipendService monthlyStipendService) {
        this.monthlyStipendService = monthlyStipendService;
    }

    @PostMapping("/calculate")
    public List<CalculationResult> calculate(@RequestParam YearMonth period) {
        return monthlyStipendService.calculateMonthlyStipend(period).stream()
                .map(CalculationResult::from)
                .toList();
    }

    public record CalculationResult(
            UUID studentId,
            UUID cohortId,
            int stipendYear,
            int stipendMonth,
            BigDecimal calculatedAmount,
            String currency,
            StipendStatus status
    ) {
        private static CalculationResult from(MonthlyStipend stipend) {
            return new CalculationResult(
                    stipend.getStudentId(),
                    stipend.getCohortId(),
                    stipend.getStipendYear(),
                    stipend.getStipendMonth(),
                    stipend.getCalculatedAmount(),
                    stipend.getCurrency(),
                    stipend.getStatus()
            );
        }
    }
}
