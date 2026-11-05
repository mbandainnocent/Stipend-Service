package com.stipend.service.Controller;

import com.stipend.service.DTO.CalculationResultDTO;
import com.stipend.service.DTO.MonthlyStipendResponseDTO;
import com.stipend.service.StipendRecords.MonthlyStipendService;
import com.stipend.service.service.DevelopmentStipendService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@Profile("dev")
@RequestMapping("/api/dev/stipends")
public class DevelopmentStipendCalculationController {

    private final MonthlyStipendService monthlyStipendService;
    private final DevelopmentStipendService developmentStipendService;

    public DevelopmentStipendCalculationController(
            MonthlyStipendService monthlyStipendService,
            DevelopmentStipendService developmentStipendService
    ) {
        this.monthlyStipendService = monthlyStipendService;
        this.developmentStipendService = developmentStipendService;
    }

    @PostMapping("/calculate")
    public List<CalculationResultDTO> calculate(@RequestParam YearMonth period) {
        return monthlyStipendService.calculateMonthlyStipend(period).stream()
                .map(CalculationResultDTO::from)
                .toList();
    }

    @GetMapping("/stipends")
    public ResponseEntity<List<MonthlyStipendResponseDTO>> getStipends() {
        return ResponseEntity.ok(developmentStipendService.calculateAndGetCurrentMonthStipends());
    }

    @GetMapping("/api/v1/studentId")
    public ResponseEntity<MonthlyStipendResponseDTO> getStipendByStudentId(@RequestParam UUID studentId) {
        return ResponseEntity.of(developmentStipendService.calculateAndGetCurrentMonthStipend(studentId));
    }

}
