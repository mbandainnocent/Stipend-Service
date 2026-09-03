package com.stipend.service.Event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;
@Builder

public record StudentRegisteredEvent(
        UUID eventId,
        UUID studentId,
        String firstName,
        String lastName,
        UUID programId,
        UUID cohortId,
        Instant registeredAt,
        Instant cohortStartDate,
        Instant cohortEndDate,
        Instant registrationDate,
        int version
) {
}
