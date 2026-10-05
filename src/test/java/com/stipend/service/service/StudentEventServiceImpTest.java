package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.Model.ProcessedEvent;
import com.stipend.service.Model.StipendStudent;
import com.stipend.service.Repository.ProcessedEventRepository;
import com.stipend.service.Repository.StipendStudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentEventServiceImpTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StipendStudentRepository stipendStudentRepository;

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
        Instant cohortStart = now.minus(Duration.ofDays(30));
        Instant cohortEnd = now.plus(Duration.ofDays(90));

        testEvent = new StudentRegisteredEvent(
                eventId,
                studentId,
                "John",
                "Doe",
                programId,
                cohortId,
                now,
                cohortStart,
                cohortEnd,
                now,
                1
        );

        studentEventService = new StudentEventServiceImp(
                processedEventRepository,
                stipendStudentRepository,
                "Africa/Kigali"
        );
    }

    @Test
    void processStudentRegistrationEvent_WhenEventAlreadyProcessed_ShouldReturnEarly() {
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        studentEventService.processStudentRegistrationEvent(testEvent);

        verify(stipendStudentRepository, never()).save(any());
        verify(processedEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void processStudentRegistrationEvent_WhenStudentExists_ShouldUpdateStudent() {
        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        StipendStudent existingStudent = StipendStudent.builder()
                .studentId(studentId)
                .firstName("OldName")
                .lastName("OldLastName")
                .build();

        when(stipendStudentRepository.findById(studentId)).thenReturn(Optional.of(existingStudent));
        when(stipendStudentRepository.save(any())).thenReturn(existingStudent);

        studentEventService.processStudentRegistrationEvent(testEvent);

        ArgumentCaptor<StipendStudent> studentCaptor = ArgumentCaptor.forClass(StipendStudent.class);
        verify(stipendStudentRepository).save(studentCaptor.capture());

        StipendStudent savedStudent = studentCaptor.getValue();
        assertEquals("John", savedStudent.getFirstName());
        assertEquals("Doe", savedStudent.getLastName());
        assertEquals(programId, savedStudent.getProgramId());
        assertEquals(cohortId, savedStudent.getCohortId());
        assertTrue(savedStudent.isActive());

        verify(processedEventRepository).saveAndFlush(any(ProcessedEvent.class));
    }

    @Test
    void processStudentRegistrationEvent_WhenStudentDoesNotExist_ShouldCreateStudent() {
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(stipendStudentRepository.findById(studentId)).thenReturn(Optional.empty());
        when(stipendStudentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        studentEventService.processStudentRegistrationEvent(testEvent);

        ArgumentCaptor<StipendStudent> studentCaptor = ArgumentCaptor.forClass(StipendStudent.class);
        verify(stipendStudentRepository).save(studentCaptor.capture());

        StipendStudent savedStudent = studentCaptor.getValue();
        assertEquals(studentId, savedStudent.getStudentId());
        assertEquals("John", savedStudent.getFirstName());
        assertEquals("Doe", savedStudent.getLastName());
        assertEquals(programId, savedStudent.getProgramId());
        assertEquals(cohortId, savedStudent.getCohortId());
        assertTrue(savedStudent.isActive());

        verify(processedEventRepository).saveAndFlush(any(ProcessedEvent.class));
    }

    @Test
    void processStudentRegistrationEvent_WhenEventIsNull_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> studentEventService.processStudentRegistrationEvent(null)
        );

        assertEquals("student registration event cannot be null", exception.getMessage());
    }

    @Test
    void processStudentRegistrationEvent_WhenEventIdIsNull_ShouldThrowException() {
        StudentRegisteredEvent invalidEvent = new StudentRegisteredEvent(
                null,
                studentId,
                "John",
                "Doe",
                programId,
                cohortId,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                1
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> studentEventService.processStudentRegistrationEvent(invalidEvent)
        );

        assertEquals("eventId is required", exception.getMessage());
    }

    @Test
    void processStudentRegistrationEvent_WhenStudentIdIsNull_ShouldThrowException() {
        StudentRegisteredEvent invalidEvent = new StudentRegisteredEvent(
                eventId,
                null,
                "John",
                "Doe",
                programId,
                cohortId,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                1
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> studentEventService.processStudentRegistrationEvent(invalidEvent)
        );

        assertEquals("studentId is required", exception.getMessage());
    }

    @Test
    void processStudentRegistrationEvent_WhenFirstNameIsBlank_ShouldThrowException() {
        StudentRegisteredEvent invalidEvent = new StudentRegisteredEvent(
                eventId,
                studentId,
                "",
                "Doe",
                programId,
                cohortId,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                1
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
        StudentRegisteredEvent invalidEvent = new StudentRegisteredEvent(
                eventId,
                studentId,
                "John",
                "Doe",
                programId,
                cohortId,
                now,
                now.plus(Duration.ofDays(30)),
                now.minus(Duration.ofDays(30)),
                now,
                1
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> studentEventService.processStudentRegistrationEvent(invalidEvent)
        );

        assertEquals("cohortEndDate must not be before cohortStartDate", exception.getMessage());
    }
}
