package com.stipend.service.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stipend_policies")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StipendPolicy {
    @Id
    @GeneratedValue
    private UUID policyId;

    private UUID programId;

    private boolean enabled;

    private BigDecimal baseAmount;

    private String currency;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;


}
