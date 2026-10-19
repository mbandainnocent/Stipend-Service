package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.Model.Student;
import com.stipend.service.Repository.StudentRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentRegistrationServiceTest {

    @Mock
    private StudentRegistrationRepository studentRegistrationRepository;

    private StudentRegistrationService studentRegistrationService;

    @BeforeEach
    void setUp() {
        studentRegistrationService = new StudentRegistrationService(
                studentRegistrationRepository,
                "Africa/Kigali"
        );
    }

    @Test
    void register_WhenStudentIsNew_ShouldCreateAndSaveRegistrationDetails() {
        UUID studentId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        StudentRegisteredEvent event = new StudentRegisteredEvent(
                UUID.randomUUID(),
                studentId,
                "John",
                "Doe",
                programId,
                cohortId,
                now,
                Instant.parse("2026-01-01T23:30:00Z"),
                Instant.parse("2026-03-31T23:30:00Z"),
                now,
                true,
                new BigDecimal("50000.00"),
                "RWF",
                2
        );
        when(studentRegistrationRepository.findById(studentId))
                .thenReturn(Optional.empty());

        studentRegistrationService.register(event);

        ArgumentCaptor<Student> studentCaptor =
                ArgumentCaptor.forClass(Student.class);
        verify(studentRegistrationRepository).save(studentCaptor.capture());
        Student savedStudent = studentCaptor.getValue();
        assertEquals(studentId, savedStudent.getStudentId());
        assertEquals("John", savedStudent.getFirstName());
        assertEquals("Doe", savedStudent.getLastName());
        assertEquals(programId, savedStudent.getProgramId());
        assertEquals(cohortId, savedStudent.getCohortId());
        assertEquals(LocalDate.of(2026, 1, 2), savedStudent.getCohortStartDate());
        assertEquals(LocalDate.of(2026, 4, 1), savedStudent.getCohortEndDate());
        assertEquals(now, savedStudent.getRegisteredAt());
        assertEquals(now, savedStudent.getRegistrationDate());
        assertEquals(2, savedStudent.getVersion());
        assertTrue(savedStudent.isActive());
        assertTrue(savedStudent.isStipendEligible());
        assertTrue(savedStudent.isCohortStipendEligible());
        assertEquals(new BigDecimal("50000.00"), savedStudent.getStipendBaseAmount());
        assertEquals("RWF", savedStudent.getStipendCurrency());
    }

    @Test
    void register_WhenCohortIsNotStipendEligible_ShouldSaveStudentAsNotEligible() {
        UUID studentId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        StudentRegisteredEvent event = new StudentRegisteredEvent(
                UUID.randomUUID(),
                studentId,
                "John",
                "Doe",
                programId,
                cohortId,
                now,
                Instant.parse("2026-01-01T23:30:00Z"),
                Instant.parse("2026-03-31T23:30:00Z"),
                now,
                false,
                null,
                null,
                2
        );
        when(studentRegistrationRepository.findById(studentId))
                .thenReturn(Optional.empty());

        studentRegistrationService.register(event);

        ArgumentCaptor<Student> studentCaptor =
                ArgumentCaptor.forClass(Student.class);
        verify(studentRegistrationRepository).save(studentCaptor.capture());
        Student savedStudent = studentCaptor.getValue();
        assertFalse(savedStudent.isStipendEligible());
        assertFalse(savedStudent.isCohortStipendEligible());
        assertNull(savedStudent.getStipendBaseAmount());
        assertNull(savedStudent.getStipendCurrency());
    }

    @Test
    void register_WhenExistingStudentIsInactive_ShouldNotReactivateStudent() {
        UUID studentId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID cohortId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        StudentRegisteredEvent event = new StudentRegisteredEvent(
                UUID.randomUUID(),
                studentId,
                "John",
                "Doe",
                programId,
                cohortId,
                now,
                Instant.parse("2026-01-01T23:30:00Z"),
                Instant.parse("2026-03-31T23:30:00Z"),
                now,
                true,
                new BigDecimal("50000.00"),
                "RWF",
                3
        );
        Student inactiveStudent = Student.builder()
                .studentId(studentId)
                .active(false)
                .statusVersion(1)
                .build();
        when(studentRegistrationRepository.findById(studentId))
                .thenReturn(Optional.of(inactiveStudent));

        studentRegistrationService.register(event);

        ArgumentCaptor<Student> studentCaptor =
                ArgumentCaptor.forClass(Student.class);
        verify(studentRegistrationRepository).save(studentCaptor.capture());
        Student savedStudent = studentCaptor.getValue();
        assertFalse(savedStudent.isActive());
        assertFalse(savedStudent.isStipendEligible());
        assertTrue(savedStudent.isCohortStipendEligible());
        assertEquals(1, savedStudent.getStatusVersion());
    }
}
