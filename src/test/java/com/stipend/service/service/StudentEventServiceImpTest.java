package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentEventServiceImpTest {

    @Mock
    private ProcessedEventService processedEventService;

    @Mock
    private StudentRegistrationService studentRegistrationService;

    private StudentEventServiceImp studentEventService;
    private StudentRegisteredEvent testEvent;
    private UUID eventId;
    private UUID studentId;
    private UUID programId;
    private UUID cohortId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        programId = UUID.randomUUID();
        cohortId = UUID.randomUUID();

        Instant now = Instant.now();
        testEvent = new StudentRegisteredEvent(
                eventId,
                studentId,
                "John",
                "Doe",
                programId,
                cohortId,
                now,
                now.minus(Duration.ofDays(30)),
                now.plus(Duration.ofDays(90)),
                now,
                1
        );

        studentEventService = new StudentEventServiceImp(
                new StudentRegistrationEventValidator(),
                processedEventService,
                studentRegistrationService
        );
    }

    @Test
    void processStudentRegistrationEvent_WhenEventAlreadyProcessed_ShouldReturnEarly() {
        when(processedEventService.isProcessed(eventId)).thenReturn(true);

        studentEventService.processStudentRegistrationEvent(testEvent);

        verify(studentRegistrationService, never()).register(any());
        verify(processedEventService, never()).markProcessed(any());
    }

    @Test
    void processStudentRegistrationEvent_WhenEventIsNew_ShouldRegisterAndMarkProcessed() {
        when(processedEventService.isProcessed(eventId)).thenReturn(false);

        studentEventService.processStudentRegistrationEvent(testEvent);

        verify(studentRegistrationService).register(testEvent);
        verify(processedEventService).markProcessed(eventId);
    }

    @Test
    void processStudentRegistrationEvent_WhenEventIsNull_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> studentEventService.processStudentRegistrationEvent(null)
        );

        assertEquals(
                "Student registration event cannot be null",
                exception.getMessage()
        );
        verifyNoInteractions(processedEventService, studentRegistrationService);
    }

    @Test
    void processStudentRegistrationEvent_WhenEventIdIsNull_ShouldThrowException() {
        StudentRegisteredEvent invalidEvent = eventWith(
                null, studentId, "John", "Doe",
                Instant.now(), Instant.now().plus(Duration.ofDays(1)), 1
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> studentEventService.processStudentRegistrationEvent(invalidEvent)
        );

        assertEquals("eventId is required", exception.getMessage());
    }

    @Test
    void processStudentRegistrationEvent_WhenStudentIdIsNull_ShouldThrowException() {
        StudentRegisteredEvent invalidEvent = eventWith(
                eventId, null, "John", "Doe",
                Instant.now(), Instant.now().plus(Duration.ofDays(1)), 1
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> studentEventService.processStudentRegistrationEvent(invalidEvent)
        );

        assertEquals("studentId is required", exception.getMessage());
    }

    @Test
    void processStudentRegistrationEvent_WhenFirstNameIsBlank_ShouldThrowException() {
        StudentRegisteredEvent invalidEvent = eventWith(
                eventId, studentId, "", "Doe",
                Instant.now(), Instant.now().plus(Duration.ofDays(1)), 1
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> studentEventService.processStudentRegistrationEvent(invalidEvent)
        );

        assertEquals("firstName is required", exception.getMessage());
    }

    @Test
    void processStudentRegistrationEvent_WhenCohortEndDateBeforeStartDate_ShouldThrowException() {
        Instant now = Instant.now();
        StudentRegisteredEvent invalidEvent = eventWith(
                eventId, studentId, "John", "Doe",
                now.plus(Duration.ofDays(1)), now, 1
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> studentEventService.processStudentRegistrationEvent(invalidEvent)
        );

        assertEquals(
                "cohortEndDate must not be before cohortStartDate",
                exception.getMessage()
        );
    }

    private StudentRegisteredEvent eventWith(
            UUID invalidEventId,
            UUID invalidStudentId,
            String firstName,
            String lastName,
            Instant cohortStart,
            Instant cohortEnd,
            int version) {
        Instant now = Instant.now();
        return new StudentRegisteredEvent(
                invalidEventId,
                invalidStudentId,
                firstName,
                lastName,
                programId,
                cohortId,
                now,
                cohortStart,
                cohortEnd,
                now,
                version
        );
    }
}
