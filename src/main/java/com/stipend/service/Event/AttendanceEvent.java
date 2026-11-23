package com.stipend.service.Event;

import com.stipend.service.Enum.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AttendanceEvent(
        UUID eventId,
        UUID attendanceId,
        UUID studentId,
        UUID programId,
        UUID cohortId,
        AttendanceStatus attendanceStatus,
        LocalDate attendanceDate,
        Instant timestamp,
        int version
) {
}
