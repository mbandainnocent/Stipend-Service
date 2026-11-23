package com.stipend.service.Model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stipend_policies")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
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
    @Schema(description = "List of payment tiers based on monthly attendance percentage")
    private List<StipendTier> tiers = new ArrayList<>();

    @Schema(description = "Timestamp when the policy was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the policy was last updated")
    private Instant updatedAt;

    public BigDecimal getPaymentPercentageForAttendance(BigDecimal attendancePercentage) {
        if (attendancePercentage == null
                || attendancePercentage.compareTo(BigDecimal.ZERO) < 0
                || attendancePercentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(
                    "Attendance percentage must be between 0 and 100"
            );
        }

        List<StipendTier> matchingTiers = tiers.stream()
                .filter(tier -> attendancePercentage.compareTo(
                        tier.getMinimumAttendancePercentage()) >= 0)
                .sorted(Comparator.comparing(StipendTier::getMinimumAttendancePercentage).reversed())
                .toList();

        if (matchingTiers.isEmpty()) {
            throw new IllegalStateException(
                    "No stipend tier for " + attendancePercentage
                            + "% attendance in policy " + policyId
            );
        }

        BigDecimal percentage = matchingTiers.get(0).getPaymentPercentage();
        if (percentage == null || percentage.compareTo(BigDecimal.ZERO) < 0
                || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException(
                    "Invalid payment percentage for " + attendancePercentage
                            + "% attendance in policy " + policyId
            );
        }
        return percentage;
    }

    public void validate() {
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Policy effectiveFrom is required");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "Policy effectiveTo cannot be before effectiveFrom"
            );
        }
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("Policy must define at least one stipend tier");
        }
        if (tiers.stream().anyMatch(tier -> tier == null)) {
            throw new IllegalArgumentException("Policy tiers cannot contain null entries");
        }
        if (tiers.stream().anyMatch(tier -> tier.getMinimumAttendancePercentage() == null)) {
            throw new IllegalArgumentException(
                    "Tier minimumAttendancePercentage is required"
            );
        }

        List<StipendTier> orderedTiers = tiers.stream()
                .sorted(Comparator.comparing(StipendTier::getMinimumAttendancePercentage))
                .toList();
        BigDecimal previousMinimum = null;
        for (int index = 0; index < orderedTiers.size(); index++) {
            StipendTier tier = orderedTiers.get(index);
            BigDecimal minimum = tier.getMinimumAttendancePercentage();
            if (minimum == null || minimum.compareTo(BigDecimal.ZERO) < 0
                    || minimum.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException(
                        "Tier minimumAttendancePercentage must be between 0 and 100"
                );
            }
            if (previousMinimum != null && minimum.compareTo(previousMinimum) <= 0) {
                throw new IllegalArgumentException(
                        "Stipend tier attendance thresholds must be unique and increasing"
                );
            }
            BigDecimal percentage = tier.getPaymentPercentage();
            if (percentage == null || percentage.compareTo(BigDecimal.ZERO) < 0
                    || percentage.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException(
                        "Tier paymentPercentage must be between 0 and 100"
                );
            }
            previousMinimum = minimum;
        }
        if (orderedTiers.get(0).getMinimumAttendancePercentage()
                .compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException(
                    "Stipend tiers must include a zero-percent attendance threshold"
            );
        }
    }
}
