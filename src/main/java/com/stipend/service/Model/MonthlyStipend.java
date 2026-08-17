package com.stipend.service.Model;

import com.stipend.service.Enum.StipendStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "monthly_stipends",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_student_stipend_month",
                        columnNames = {"studentId", "stipendYear", "stipendMonth"}
                )
        }
)
public class MonthlyStipend {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID studentId;
    private UUID cohortId;
    private UUID policyId;

    private int stipendYear;
    private int stipendMonth;

    private int absenceCount;

    private BigDecimal baseAmount;
    private BigDecimal paymentPercentage;
    private BigDecimal calculatedAmount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private StipendStatus status;

    private Instant calculatedAt;
    private Instant updatedAt;
}
