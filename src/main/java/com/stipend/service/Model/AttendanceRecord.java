package com.stipend.service.Model;

import com.stipend.service.Enum.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Schema(description = "Represents an attendance record for a student in the stipend system")
public class AttendanceRecord {

    @Id
    @Schema(description = "Unique identifier for the attendance record, provided by Kafka source system")
    private UUID attendanceId;

    @Schema(description = "ID of the student")
    private UUID studentId;

    @Schema(description = "ID of the cohort")
    private UUID cohortId;

    @Schema(description = "ID of the program")
    private UUID programId;

    @Schema(description = "Attendance status of the student")
    private AttendanceStatus status;

    @Schema(description = "Date of the attendance record")
    private LocalDate date;

    @Schema(description = "Timestamp when the record was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the record was last updated")
    private LocalDateTime updatedAt;
}
