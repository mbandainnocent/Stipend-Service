package com.stipend.service.Event;

import java.time.Instant;
import java.util.UUID;

public record StudentRegisteredEvent(
        UUID eventId,
        UUID studentId,
        String firstName,
        String lastName,
        UUID programId,
        UUID cohortId,
        Instant registeredAt,
        int version
) {
}
