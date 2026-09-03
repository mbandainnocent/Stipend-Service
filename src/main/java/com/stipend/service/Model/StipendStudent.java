package com.stipend.service.Model;

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
public class StipendStudent {

    @Id
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "cohort_id", nullable = false)
    private UUID cohortId;

    @Column(name = "cohort_start_date", nullable = false)
    private LocalDate cohortStartDate;

    @Column(name = "cohort_end_date", nullable = false)
    private LocalDate cohortEndDate;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
