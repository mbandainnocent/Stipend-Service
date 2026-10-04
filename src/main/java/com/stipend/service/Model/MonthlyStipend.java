package com.stipend.service.Model;

import com.stipend.service.Enum.StipendStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Schema(description = "Represents a monthly stipend calculation and payment record for a student")
public class MonthlyStipend {

    @Id
    @GeneratedValue
    @Schema(description = "Unique identifier for the monthly stipend record")
    private UUID id;

    @Schema(description = "ID of the student")
    private UUID studentId;
    @Schema(description = "ID of the cohort")
    private UUID cohortId;
    @Schema(description = "ID of the stipend policy applied")
    private UUID policyId;

    @Schema(description = "Year of the stipend period")
    private int stipendYear;
    @Schema(description = "Month of the stipend period (1-12)")
    private int stipendMonth;

    @Schema(description = "Number of absences recorded for the month")
    private int absenceCount;

    @Schema(description = "Base amount from the stipend policy")
    private BigDecimal baseAmount;
    @Schema(description = "Payment percentage applied based on absence count")
    private BigDecimal paymentPercentage;
    @Schema(description = "Final calculated amount to be paid")
    private BigDecimal calculatedAmount;

    @Schema(description = "Currency code for the payment")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Schema(description = "Current status of the stipend payment")
    private StipendStatus status;

    @Schema(description = "User who approved the payment")
    private String approvedBy;
    @Schema(description = "Comment regarding the approval")
    private String approvalComment;
    @Schema(description = "Timestamp when the payment was approved")
    private Instant approvedAt;
    @Schema(description = "Reference number for the payment")
    private String paymentReference;
    @Schema(description = "Timestamp when the payment was made")
    private Instant paidAt;

    @Schema(description = "Timestamp when the stipend was calculated")
    private Instant calculatedAt;
    @Schema(description = "Timestamp when the record was last updated")
    private Instant updatedAt;
}
