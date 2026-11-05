package com.stipend.service.Controller;

import com.stipend.service.DTO.MonthlyStipendPageDTO;
import com.stipend.service.DTO.MonthlyStipendResponseDTO;
import com.stipend.service.DTO.ApproveStipendRequestDTO;
import com.stipend.service.DTO.MarkPaidStipendRequestDTO;
import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.service.FinanceStipendReadService;
import com.stipend.service.service.FinanceStipendWorkflowService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1")
public class FinanceStipendController {

    private static final int MIN_YEAR = 2020;
    private static final int MAX_YEAR = 2100;
    private static final int MAX_PAGE_SIZE = 100;

    private final FinanceStipendReadService financeStipendReadService;
    private final FinanceStipendWorkflowService financeStipendWorkflowService;

    public FinanceStipendController(
            FinanceStipendReadService financeStipendReadService,
            FinanceStipendWorkflowService financeStipendWorkflowService
    ) {
        this.financeStipendReadService = financeStipendReadService;
        this.financeStipendWorkflowService = financeStipendWorkflowService;
    }

    @GetMapping("/stipends")
    public MonthlyStipendPageDTO searchStipends(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) UUID cohortId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) StipendStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validateYear(year);
        validateMonth(month);
        validatePage(page, size);
        return financeStipendReadService.searchStipends(
                year,
                month,
                cohortId,
                studentId,
                status,
                PageRequest.of(page, size)
        );
    }

    @GetMapping("/stipends/{stipendId}")
    public MonthlyStipendResponseDTO getStipend(@PathVariable UUID stipendId) {
        return financeStipendReadService.getStipend(stipendId);
    }

    @GetMapping("/students/{studentId}/stipends")
    public List<MonthlyStipendResponseDTO> getStudentHistory(
            @PathVariable UUID studentId,
            @RequestParam(required = false) Integer year
    ) {
        validateYear(year);
        return financeStipendReadService.getStudentHistory(studentId, year);
    }

    @GetMapping("/cohorts/{cohortId}/stipends")
    public List<MonthlyStipendResponseDTO> getCohortStipends(
            @PathVariable UUID cohortId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) StipendStatus status
    ) {
        validateYear(year);
        validateMonth(month);
        return financeStipendReadService.getCohortStipends(cohortId, year, month, status);
    }

    @PatchMapping("/stipends/{stipendId}/approve")
    public MonthlyStipendResponseDTO approveStipend(
            @PathVariable UUID stipendId,
            @RequestBody(required = false) ApproveStipendRequestDTO request,
            Principal principal
    ) {
        return financeStipendWorkflowService.approve(
                stipendId,
                principal == null ? null : principal.getName(),
                request
        );
    }

    @PatchMapping("/stipends/{stipendId}/mark-paid")
    public MonthlyStipendResponseDTO markStipendPaid(
            @PathVariable UUID stipendId,
            @RequestBody MarkPaidStipendRequestDTO request,
            Principal principal
    ) {
        return financeStipendWorkflowService.markPaid(
                stipendId,
                principal == null ? null : principal.getName(),
                request
        );
    }

    private void validateYear(Integer year) {
        if (year != null && (year < MIN_YEAR || year > MAX_YEAR)) {
            throw new IllegalArgumentException("year must be between 2020 and 2100");
        }
    }

    private void validateMonth(Integer month) {
        if (month != null && (month < 1 || month > 12)) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }
}
