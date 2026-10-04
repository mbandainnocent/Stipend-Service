package com.stipend.service.Model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stipend_students")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Schema(description = "Represents a student enrolled in a stipend program")
public class StipendStudent {

    @Id
    @Schema(description = "Unique identifier for the student")
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Schema(description = "First name of the student")
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Schema(description = "Last name of the student")
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Schema(description = "ID of the program the student is enrolled in")
    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Schema(description = "ID of the cohort the student belongs to")
    @Column(name = "cohort_id", nullable = false)
    private UUID cohortId;

    @Schema(description = "Start date of the student's cohort")
    @Column(name = "cohort_start_date", nullable = false)
    private LocalDate cohortStartDate;

    @Schema(description = "End date of the student's cohort")
    @Column(name = "cohort_end_date", nullable = false)
    private LocalDate cohortEndDate;

    @Schema(description = "Whether the student is currently active in the program")
    @Column(nullable = false)
    private boolean active;

    @Schema(description = "Timestamp when the student was registered")
    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Schema(description = "Timestamp when the student record was last updated")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
