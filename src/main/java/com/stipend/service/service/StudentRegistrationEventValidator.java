package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;
import org.springframework.stereotype.Service;

@Service
public class StudentRegistrationEventValidator {

    public void validate(StudentRegisteredEvent event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Student registration event cannot be null"
            );
        }

        if (event.eventId() == null) {
            throw new IllegalArgumentException("eventId is required");
        }

        if (event.studentId() == null) {
            throw new IllegalArgumentException("studentId is required");
        }

        if (event.firstName() == null || event.firstName().isBlank()) {
            throw new IllegalArgumentException("firstName is required");
        }

        if (event.lastName() == null || event.lastName().isBlank()) {
            throw new IllegalArgumentException("lastName is required");
        }

        if (event.programId() == null) {
            throw new IllegalArgumentException("programId is required");
        }

        if (event.cohortId() == null) {
            throw new IllegalArgumentException("cohortId is required");
        }

        if (event.cohortStartDate() == null) {
            throw new IllegalArgumentException("cohortStartDate is required");
        }

        if (event.cohortEndDate() == null) {
            throw new IllegalArgumentException("cohortEndDate is required");
        }

        if (event.cohortEndDate().isBefore(event.cohortStartDate())) {
            throw new IllegalArgumentException(
                    "cohortEndDate must not be before cohortStartDate"
            );
        }

        if (event.registeredAt() == null) {
            throw new IllegalArgumentException("registeredAt is required");
        }

        if (event.registrationDate() == null) {
            throw new IllegalArgumentException("registrationDate is required");
        }

        if (event.version() < 1) {
            throw new IllegalArgumentException(
                    "version must be greater than or equal to 1"
            );
        }
    }
}
