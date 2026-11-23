package com.stipend.service.Event;

import com.stipend.service.Enum.StudentStatus;

import java.util.UUID;

public record StudentStatusChangedEvent(
        UUID eventId,
        UUID studentId,
        StudentStatus status,
        int version
) {
}
