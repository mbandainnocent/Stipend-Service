package com.stipend.service.Model;

import jakarta.persistence.GeneratedValue;
import org.springframework.data.annotation.Id;

import java.math.BigDecimal;
import java.util.UUID;

public class StipendTier {
    @Id
    @GeneratedValue
    private UUID id;

    private UUID policyId;

    private int minimumAbsences;
    private Integer maximumAbsences;

    private BigDecimal paymentPercentage;
}
