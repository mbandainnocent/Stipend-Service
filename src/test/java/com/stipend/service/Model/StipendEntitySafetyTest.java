package com.stipend.service.Model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stipend.service.DTO.StipendPolicyResponseDTO;
import com.stipend.service.DTO.StipendTierResponseDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StipendEntitySafetyTest {

    @Test
    void policyAndTierDoNotGenerateMethodsThatTraverseTheirRelationship() {
        assertNoGeneratedRelationshipMethods(StipendPolicy.class);
        assertNoGeneratedRelationshipMethods(StipendTier.class);

        StipendPolicy policy = StipendPolicy.builder()
                .policyId(UUID.randomUUID())
                .tiers(List.of())
                .build();
        StipendTier tier = StipendTier.builder()
                .id(UUID.randomUUID())
                .policy(policy)
                .minimumAttendancePercentage(BigDecimal.ZERO)
                .paymentPercentage(new BigDecimal("100.00"))
                .build();
        policy.setTiers(List.of(tier));

        assertThat(tier.getPolicy()).isSameAs(policy);
        assertThat(policy.getTiers()).containsExactly(tier);
        assertThat(policy.toString()).doesNotContain("tiers");
        assertThat(tier.toString()).doesNotContain("policy");
    }

    @Test
    void policyResponseSerializationContainsTiersWithoutEntityBackReference() throws Exception {
        UUID policyId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        StipendPolicyResponseDTO response = new StipendPolicyResponseDTO(
                policyId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1),
                null,
                true,
                List.of(new StipendTierResponseDTO(
                        tierId,
                        BigDecimal.ZERO,
                        new BigDecimal("100.00")
                )),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        JsonNode json = new ObjectMapper().findAndRegisterModules().valueToTree(response);

        assertThat(json.get("id").asText()).isEqualTo(policyId.toString());
        assertThat(json.get("effectiveTo").isNull()).isTrue();
        assertThat(json.get("tiers")).hasSize(1);
        assertThat(json.get("tiers").get(0).get("id").asText()).isEqualTo(tierId.toString());
        assertThat(json.get("tiers").get(0).has("policy")).isFalse();
    }

    private void assertNoGeneratedRelationshipMethods(Class<?> entityType) {
        assertThat(entityType.getDeclaredMethods())
                .extracting(Method::getName)
                .doesNotContain("toString", "equals", "hashCode");
    }
}
