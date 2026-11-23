package com.stipend.service.Controller;

import com.stipend.service.Exception.GlobalExceptionHandler;
import com.stipend.service.Exception.ResourceNotFoundException;
import com.stipend.service.service.StipendPolicyReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StipendPolicyControllerTest {

    private StipendPolicyReadService readService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        readService = mock(StipendPolicyReadService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StipendPolicyController(readService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchesPoliciesWithOptionalFilters() throws Exception {
        UUID programId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        when(readService.searchPolicies(programId, cohortId, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stipend-policies")
                        .param("programId", programId.toString())
                        .param("cohortId", cohortId.toString())
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(readService).searchPolicies(programId, cohortId, true);
    }

    @Test
    void rejectsInvalidPolicyFilterAndReturnsNotFoundForUnknownPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/stipend-policies").param("enabled", "maybe"))
                .andExpect(status().isBadRequest());

        UUID policyId = UUID.randomUUID();
        when(readService.getPolicy(policyId))
                .thenThrow(new ResourceNotFoundException("Stipend policy not found: " + policyId));

        mockMvc.perform(get("/api/v1/stipend-policies/{policyId}", policyId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
