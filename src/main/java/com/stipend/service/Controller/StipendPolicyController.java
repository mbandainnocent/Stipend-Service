package com.stipend.service.Controller;

import com.stipend.service.DTO.StipendPolicyResponseDTO;
import com.stipend.service.service.StipendPolicyReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stipend-policies")
public class StipendPolicyController {

    private final StipendPolicyReadService stipendPolicyReadService;

    public StipendPolicyController(StipendPolicyReadService stipendPolicyReadService) {
        this.stipendPolicyReadService = stipendPolicyReadService;
    }

    @GetMapping
    public List<StipendPolicyResponseDTO> searchPolicies(
            @RequestParam(required = false) UUID programId,
            @RequestParam(required = false) UUID cohortId,
            @RequestParam(required = false) Boolean enabled
    ) {
        return stipendPolicyReadService.searchPolicies(programId, cohortId, enabled);
    }

    @GetMapping("/{policyId}")
    public StipendPolicyResponseDTO getPolicy(@PathVariable UUID policyId) {
        return stipendPolicyReadService.getPolicy(policyId);
    }
}
