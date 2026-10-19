package com.stipend.service.Event;

import java.util.UUID;

public record StudentStatusChangedEvent(
        UUID eventId,
        UUID studentId,
        Status status,
        int version
) {
    public enum Status {
        ACTIVE,
        DROPPED_OUT
    }
}
