package com.stipend.service.Model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stipend_policies")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Schema(description = "Represents a stipend policy with tiered payment structure based on attendance")
public class StipendPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Schema(description = "Unique identifier for the stipend policy")
    private UUID policyId;

    @Schema(description = "ID of the program this policy applies to")
    private UUID programId;

    @Schema(description = "ID of the cohort this policy applies to")
    private UUID cohortId;

    @Schema(description = "Whether the policy is currently active")
    private boolean enabled;

    @Schema(description = "Effective start date of the policy")
    private LocalDate effectiveFrom;

    @Schema(description = "Effective end date of the policy")
    private LocalDate effectiveTo;

    @OneToMany(
            mappedBy = "policy",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    @Schema(description = "List of payment tiers based on absence thresholds")
    private List<StipendTier> tiers = new ArrayList<>();

    @Schema(description = "Timestamp when the policy was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the policy was last updated")
    private Instant updatedAt;

    public BigDecimal getPaymentPercentageForAbsences(int absenceCount) {
        if (absenceCount < 0) {
            throw new IllegalArgumentException("Absence count cannot be negative");
        }

        List<StipendTier> matchingTiers = tiers.stream()
                .filter(tier -> absenceCount >= tier.getMinimumAbsences())
                .filter(tier -> tier.getMaximumAbsences() == null
                        || absenceCount <= tier.getMaximumAbsences())
                .toList();

        if (matchingTiers.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one stipend tier for " + absenceCount
                            + " absences in policy " + policyId
            );
        }

        BigDecimal percentage = matchingTiers.get(0).getPaymentPercentage();
        if (percentage == null || percentage.compareTo(BigDecimal.ZERO) < 0
                || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException(
                    "Invalid payment percentage for " + absenceCount
                            + " absences in policy " + policyId
            );
        }
        return percentage;
    }
}
