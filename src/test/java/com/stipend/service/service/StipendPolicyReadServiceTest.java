package com.stipend.service.service;

import com.stipend.service.DTO.StipendPolicyResponseDTO;
import com.stipend.service.Exception.ResourceNotFoundException;
import com.stipend.service.Model.StipendPolicy;
import com.stipend.service.Model.StipendTier;
import com.stipend.service.Repository.StipendPolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StipendPolicyReadServiceTest {

    @Mock
    private StipendPolicyRepository policyRepository;

    @InjectMocks
    private StipendPolicyReadService service;

    @Test
    void searchPoliciesMapsPolicyAndSortedTiers() {
        UUID programId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        StipendPolicy policy = policy(programId, cohortId);
        when(policyRepository.searchPolicies(programId, cohortId, true, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(policy)));

        List<StipendPolicyResponseDTO> result = service.searchPolicies(programId, cohortId, true);

        assertEquals(1, result.size());
        StipendPolicyResponseDTO response = result.get(0);
        assertEquals(policy.getPolicyId(), response.id());
        assertEquals(programId, response.programId());
        assertEquals(cohortId, response.cohortId());
        assertEquals(LocalDate.of(2025, 1, 1), response.effectiveFrom());
        assertEquals(true, response.enabled());
        assertEquals(List.of(new BigDecimal("0.00"), new BigDecimal("90.00")), response.tiers().stream()
                .map(tier -> tier.minimumAttendancePercentage())
                .toList());
        assertEquals(new BigDecimal("90.00"), response.tiers().get(1).paymentPercentage());
    }

    @Test
    void getPolicyMapsTiersWithinReadService() {
        UUID policyId = UUID.randomUUID();
        StipendPolicy policy = policy(UUID.randomUUID(), UUID.randomUUID());
        policy.setPolicyId(policyId);
        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));

        StipendPolicyResponseDTO result = service.getPolicy(policyId);

        assertEquals(policyId, result.id());
        assertEquals(2, result.tiers().size());
        assertEquals(LocalDate.of(2025, 12, 31), result.effectiveTo());
    }

    @Test
    void getMissingPolicyThrowsNotFound() {
        UUID policyId = UUID.randomUUID();
        when(policyRepository.findById(policyId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getPolicy(policyId));
    }

    private StipendPolicy policy(UUID programId, UUID cohortId) {
        StipendPolicy policy = StipendPolicy.builder()
                .policyId(UUID.randomUUID())
                .programId(programId)
                .cohortId(cohortId)
                .enabled(true)
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .effectiveTo(LocalDate.of(2025, 12, 31))
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .tiers(List.of(
                        tier("90.00", "90.00"),
                        tier("0.00", "50.00")
                ))
                .build();
        policy.getTiers().forEach(tier -> tier.setPolicy(policy));
        return policy;
    }

    private StipendTier tier(String minimumAttendance, String percentage) {
        return StipendTier.builder()
                .id(UUID.randomUUID())
                .minimumAttendancePercentage(new BigDecimal(minimumAttendance))
                .paymentPercentage(new BigDecimal(percentage))
                .build();
    }
}
