package com.stipend.service.service;

import com.stipend.service.Event.StudentStatusChangedEvent;
import org.springframework.stereotype.Service;

@Service
public class StudentStatusChangedEventValidator {

    public void validate(StudentStatusChangedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Student status changed event cannot be null"
            );
        }
        if (event.eventId() == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (event.studentId() == null) {
            throw new IllegalArgumentException("studentId is required");
        }
        if (event.status() == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (event.version() < 1) {
            throw new IllegalArgumentException(
                    "version must be greater than or equal to 1"
            );
        }
    }
}
