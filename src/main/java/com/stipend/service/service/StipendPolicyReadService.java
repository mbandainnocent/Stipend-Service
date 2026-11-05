package com.stipend.service.service;

import com.stipend.service.DTO.StipendPolicyResponseDTO;
import com.stipend.service.DTO.StipendTierResponseDTO;
import com.stipend.service.Exception.ResourceNotFoundException;
import com.stipend.service.Model.StipendPolicy;
import com.stipend.service.Model.StipendTier;
import com.stipend.service.Repository.StipendPolicyRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StipendPolicyReadService {

    private final StipendPolicyRepository stipendPolicyRepository;

    public StipendPolicyReadService(StipendPolicyRepository stipendPolicyRepository) {
        this.stipendPolicyRepository = stipendPolicyRepository;
    }

    public List<StipendPolicyResponseDTO> searchPolicies(
            UUID programId,
            UUID cohortId,
            Boolean enabled
    ) {
        return stipendPolicyRepository.searchPolicies(
                        programId,
                        cohortId,
                        enabled,
                        Pageable.unpaged()
                )
                .getContent()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public StipendPolicyResponseDTO getPolicy(UUID policyId) {
        StipendPolicy policy = stipendPolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stipend policy not found: " + policyId
                ));
        return toResponse(policy);
    }

    private StipendPolicyResponseDTO toResponse(StipendPolicy policy) {
        List<StipendTierResponseDTO> tiers = policy.getTiers().stream()
                .sorted(Comparator.comparingInt(StipendTier::getMinimumAbsences))
                .map(tier -> new StipendTierResponseDTO(
                        tier.getId(),
                        tier.getMinimumAbsences(),
                        tier.getMaximumAbsences(),
                        tier.getPaymentPercentage()
                ))
                .toList();
        return new StipendPolicyResponseDTO(
                policy.getPolicyId(),
                policy.getProgramId(),
                policy.getCohortId(),
                policy.getEffectiveFrom(),
                policy.getEffectiveTo(),
                policy.isEnabled(),
                tiers,
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
