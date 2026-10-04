package com.stipend.service.Model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "stipend_tiers")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Schema(description = "Represents a payment tier within a stipend policy based on absence thresholds")
public class StipendTier {
    @Id
    @GeneratedValue
    @Schema(description = "Unique identifier for the stipend tier")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    @Schema(description = "The stipend policy this tier belongs to")
    private StipendPolicy policy;

    @Column(name = "minimum_absences", nullable = false)
    @Schema(description = "Minimum number of absences to qualify for this tier")
    private int minimumAbsences;

    @Column(name = "maximum_absences")
    @Schema(description = "Maximum number of absences for this tier (null if no upper limit)")
    private Integer maximumAbsences;

    @Column(name = "payment_percentage", nullable = false, precision = 5, scale = 2)
    @Schema(description = "Payment percentage to apply for this tier")
    private BigDecimal paymentPercentage;
}
