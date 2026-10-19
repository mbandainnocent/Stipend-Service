package com.stipend.service.Event;


import java.math.BigDecimal;
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
        Instant cohortStartDate,
        Instant cohortEndDate,
        Instant registrationDate,
        Boolean stipendEligible,
        BigDecimal monthlyStipendAmount,
        String stipendCurrency,
        int version
) {
}
