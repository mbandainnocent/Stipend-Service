package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.Model.StipendStudent;
import com.stipend.service.Repository.StipendStudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentRegistrationServiceTest {

    @Mock
    private StipendStudentRepository stipendStudentRepository;

    private StudentRegistrationService studentRegistrationService;

    @BeforeEach
    void setUp() {
        studentRegistrationService = new StudentRegistrationService(
                stipendStudentRepository,
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
                2
        );
        when(stipendStudentRepository.findById(studentId))
                .thenReturn(Optional.empty());

        studentRegistrationService.register(event);

        ArgumentCaptor<StipendStudent> studentCaptor =
                ArgumentCaptor.forClass(StipendStudent.class);
        verify(stipendStudentRepository).save(studentCaptor.capture());
        StipendStudent savedStudent = studentCaptor.getValue();
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
    }
}
