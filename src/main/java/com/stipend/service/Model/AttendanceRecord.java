package com.stipend.service.Model;

import com.stipend.service.Enum.AttendanceStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity
@Table(
        name = "stipend_attendance",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stipend_attendance_id",
                        columnNames = "attendanceId"
                )
        }
)
public class AttendanceRecord {

    @Id

    private UUID attendanceId;

    private UUID studentId;

    private UUID cohortId;

    private UUID programId;

    private AttendanceStatus status;

    private LocalDate date;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
