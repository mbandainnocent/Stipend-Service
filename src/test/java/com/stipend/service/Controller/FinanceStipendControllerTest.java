package com.stipend.service.Controller;

import com.stipend.service.DTO.MonthlyStipendPageDTO;
import com.stipend.service.DTO.MonthlyStipendResponseDTO;
import com.stipend.service.Enum.StipendStatus;
import com.stipend.service.Exception.FinancePrincipalRequiredException;
import com.stipend.service.service.FinanceStipendWorkflowService;
import com.stipend.service.Exception.GlobalExceptionHandler;
import com.stipend.service.Exception.InvalidStipendTransitionException;
import com.stipend.service.Exception.ResourceNotFoundException;
import com.stipend.service.service.FinanceStipendReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.mock;

class FinanceStipendControllerTest {

    private FinanceStipendReadService readService;
    private FinanceStipendWorkflowService workflowService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        readService = mock(FinanceStipendReadService.class);
        workflowService = mock(FinanceStipendWorkflowService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FinanceStipendController(readService, workflowService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchStipendsPassesFiltersAndPaginationToService() throws Exception {
        UUID cohortId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(readService.searchStipends(
                eq(2026), eq(8), eq(cohortId), eq(studentId), eq(StipendStatus.CALCULATED), any()
        )).thenReturn(new MonthlyStipendPageDTO(List.of(), 2, 5, 11, 3));

        mockMvc.perform(get("/api/v1/stipends")
                        .param("year", "2026")
                        .param("month", "8")
                        .param("cohortId", cohortId.toString())
                        .param("studentId", studentId.toString())
                        .param("status", "CALCULATED")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(readService).searchStipends(
                eq(2026), eq(8), eq(cohortId), eq(studentId), eq(StipendStatus.CALCULATED), any()
        );
    }

    @Test
    void rejectsInvalidSearchParameters() throws Exception {
        mockMvc.perform(get("/api/v1/stipends").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/stipends").param("size", "101"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/stipends").param("cohortId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStipendReturnsNotFoundWhenServiceCannotFindRecord() throws Exception {
        UUID stipendId = UUID.randomUUID();
        when(readService.getStipend(stipendId))
                .thenThrow(new ResourceNotFoundException("Monthly stipend not found: " + stipendId));

        mockMvc.perform(get("/api/v1/stipends/{stipendId}", stipendId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getStudentHistoryValidatesYearAndReturnsNotFoundForUnknownStudent() throws Exception {
        UUID studentId = UUID.randomUUID();
        when(readService.getStudentHistory(studentId, 2025))
                .thenThrow(new ResourceNotFoundException("Student not found: " + studentId));

        mockMvc.perform(get("/api/v1/students/{studentId}/stipends", studentId).param("year", "2025"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/students/{studentId}/stipends", studentId).param("year", "2101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCohortStipendsRequiresPeriodAndValidatesMonth() throws Exception {
        UUID cohortId = UUID.randomUUID();
        when(readService.getCohortStipends(cohortId, 2025, 6, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/stipends", cohortId)
                        .param("year", "2025")
                        .param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/stipends", cohortId)
                        .param("year", "2025")
                        .param("month", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/cohorts/{cohortId}/stipends", cohortId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approveRequiresPrincipalAndReturnsConflictForInvalidTransition() throws Exception {
        UUID stipendId = UUID.randomUUID();
        when(workflowService.approve(eq(stipendId), eq("finance.user"), any()))
                .thenThrow(new InvalidStipendTransitionException("Already approved"));
        when(workflowService.approve(eq(stipendId), isNull(), any()))
                .thenThrow(new FinancePrincipalRequiredException());
        when(workflowService.markPaid(eq(stipendId), isNull(), any()))
                .thenThrow(new FinancePrincipalRequiredException());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/stipends/{stipendId}/approve", stipendId)
                        .principal(() -> "finance.user")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Reviewed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/stipends/{stipendId}/approve", stipendId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markPaidRejectsMissingBodyAsBadRequest() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/stipends/{stipendId}/mark-paid", UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markPaidRequiresPrincipal() throws Exception {
        UUID stipendId = UUID.randomUUID();
        when(workflowService.markPaid(eq(stipendId), isNull(), any()))
                .thenThrow(new FinancePrincipalRequiredException());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/stipends/{stipendId}/mark-paid", stipendId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"payment-123\",\"paidAt\":\"2026-09-05T14:30:00Z\"}"))
                .andExpect(status().isUnauthorized());
    }
}
