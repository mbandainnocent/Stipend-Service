package com.stipend.service.service;

import com.stipend.service.Event.StudentStatusChangedEvent;
import com.stipend.service.Exception.ResourceNotFoundException;
import com.stipend.service.Model.Student;
import com.stipend.service.Repository.StudentRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentStatusChangedEventServiceTest {

    @Mock
    private ProcessedEventService processedEventService;

    @Mock
    private StudentRegistrationRepository studentRepository;

    private StudentStatusChangedEventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new StudentStatusChangedEventService(
                new StudentStatusChangedEventValidator(),
                processedEventService,
                studentRepository
        );
    }

    @Test
    void process_WhenStudentDropsOut_ShouldDeactivateAndRemoveFutureEligibility() {
        UUID eventId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder()
                .studentId(studentId)
                .active(true)
                .stipendEligible(true)
                .cohortStipendEligible(true)
                .stipendBaseAmount(new BigDecimal("50000.00"))
                .statusVersion(0)
                .build();
        StudentStatusChangedEvent event = new StudentStatusChangedEvent(
                eventId,
                studentId,
                StudentStatusChangedEvent.Status.DROPPED_OUT,
                1
        );
        when(processedEventService.isProcessed(eventId)).thenReturn(false);
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));

        eventService.process(event);

        assertFalse(student.isActive());
        assertFalse(student.isStipendEligible());
        assertTrue(student.isCohortStipendEligible());
        assertEquals(new BigDecimal("50000.00"), student.getStipendBaseAmount());
        assertEquals(1, student.getStatusVersion());
        verify(studentRepository).save(student);
        verify(processedEventService).markProcessed(eventId, "STUDENT_STATUS_CHANGED");
    }

    @Test
    void process_WhenStatusEventReactivatesEligibleStudent_ShouldRestoreEligibility() {
        UUID eventId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder()
                .studentId(studentId)
                .active(false)
                .stipendEligible(false)
                .cohortStipendEligible(true)
                .statusVersion(1)
                .build();
        StudentStatusChangedEvent event = new StudentStatusChangedEvent(
                eventId,
                studentId,
                StudentStatusChangedEvent.Status.ACTIVE,
                2
        );
        when(processedEventService.isProcessed(eventId)).thenReturn(false);
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));

        eventService.process(event);

        assertTrue(student.isActive());
        assertTrue(student.isStipendEligible());
        assertEquals(2, student.getStatusVersion());
        verify(studentRepository).save(student);
    }

    @Test
    void process_WhenStatusEventIsStale_ShouldNotOverwriteCurrentStatus() {
        UUID eventId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Student student = Student.builder()
                .studentId(studentId)
                .active(false)
                .stipendEligible(false)
                .statusVersion(3)
                .build();
        StudentStatusChangedEvent event = new StudentStatusChangedEvent(
                eventId,
                studentId,
                StudentStatusChangedEvent.Status.ACTIVE,
                2
        );
        when(processedEventService.isProcessed(eventId)).thenReturn(false);
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));

        eventService.process(event);

        assertFalse(student.isActive());
        assertFalse(student.isStipendEligible());
        verify(studentRepository, never()).save(any());
        verify(processedEventService).markProcessed(eventId, "STUDENT_STATUS_CHANGED");
    }

    @Test
    void process_WhenEventWasAlreadyProcessed_ShouldReturnWithoutLoadingStudent() {
        UUID eventId = UUID.randomUUID();
        StudentStatusChangedEvent event = new StudentStatusChangedEvent(
                eventId,
                UUID.randomUUID(),
                StudentStatusChangedEvent.Status.DROPPED_OUT,
                1
        );
        when(processedEventService.isProcessed(eventId)).thenReturn(true);

        eventService.process(event);

        verifyNoInteractions(studentRepository);
        verify(processedEventService, never()).markProcessed(any(), any());
    }

    @Test
    void process_WhenStudentDoesNotExist_ShouldThrowAndLeaveEventUnprocessed() {
        UUID eventId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        StudentStatusChangedEvent event = new StudentStatusChangedEvent(
                eventId,
                studentId,
                StudentStatusChangedEvent.Status.DROPPED_OUT,
                1
        );
        when(processedEventService.isProcessed(eventId)).thenReturn(false);
        when(studentRepository.findById(studentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> eventService.process(event));

        verify(processedEventService, never()).markProcessed(any(), any());
    }
}
