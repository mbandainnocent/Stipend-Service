package com.stipend.service.Model;

import com.stipend.service.Enum.StipendEnrollmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "stipend_enrollments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stipend_enrollment_student",
                        columnNames = "student_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StipendEnrollment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /*
     * These IDs come from the StudentRegistrationEvent.
     * They are references, not JPA relationships.
     */
    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "program_id", nullable = false, updatable = false)
    private UUID programId;

    @Column(name = "cohort_id", nullable = false, updatable = false)
    private UUID cohortId;

    @Column(name = "email", nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StipendEnrollmentStatus status;

    /*
     * Registration time received from the Attendance Service event.
     */
    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    /*
     * Time the Stipend Service created this enrollment.
     */
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;
}
