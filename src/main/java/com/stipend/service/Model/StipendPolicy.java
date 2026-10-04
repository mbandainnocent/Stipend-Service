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

    @Schema(description = "Base amount for the stipend before adjustments")
    private BigDecimal baseAmount;

    @Schema(description = "Currency code for the stipend")
    private String currency;

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

}
