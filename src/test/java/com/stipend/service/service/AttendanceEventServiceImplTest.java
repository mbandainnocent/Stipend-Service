package com.stipend.service.service;

import com.stipend.service.Enum.AttendanceStatus;
import com.stipend.service.Event.AttendanceEvent;
import com.stipend.service.Model.AttendanceRecord;
import com.stipend.service.Repository.AttendanceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceEventServiceImplTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private ProcessedEventService processedEventService;

    private AttendanceEventServiceImpl attendanceEventService;

    @BeforeEach
    void setUp() {
        attendanceEventService = new AttendanceEventServiceImpl(
                attendanceRecordRepository,
                processedEventService
        );
    }

    @Test
    void processAttendanceEvent_createsRecordAndMarksEventProcessed() {
        AttendanceEvent event = validEvent();
        when(processedEventService.isProcessed(event.eventId())).thenReturn(false);
        when(attendanceRecordRepository.findByIdForUpdate(event.attendanceId()))
                .thenReturn(Optional.empty());

        attendanceEventService.processAttendanceEvent(event);

        ArgumentCaptor<AttendanceRecord> recordCaptor =
                ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRecordRepository).saveAndFlush(recordCaptor.capture());
        AttendanceRecord savedRecord = recordCaptor.getValue();
        assertEquals(event.attendanceId(), savedRecord.getAttendanceId());
        assertEquals(event.studentId(), savedRecord.getStudentId());
        assertEquals(event.programId(), savedRecord.getProgramId());
        assertEquals(event.cohortId(), savedRecord.getCohortId());
        assertEquals(event.attendanceStatus(), savedRecord.getStatus());
        assertEquals(event.attendanceDate(), savedRecord.getDate());
        assertEquals(event.version(), savedRecord.getEventVersion());
        assertEquals(
                LocalDateTime.ofInstant(event.timestamp(), ZoneOffset.UTC),
                savedRecord.getCreatedAt()
        );
        verify(processedEventService).markProcessed(
                event.eventId(),
                "ATTENDANCE_RECORDED"
        );
    }

    @Test
    void processAttendanceEvent_updatesExistingRecordAndPreservesCreatedAt() {
        AttendanceEvent event = validEvent();
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 1, 1, 0, 0);
        AttendanceRecord existingRecord = AttendanceRecord.builder()
                .attendanceId(event.attendanceId())
                .createdAt(originalCreatedAt)
                .build();
        when(processedEventService.isProcessed(event.eventId())).thenReturn(false);
        when(attendanceRecordRepository.findByIdForUpdate(event.attendanceId()))
                .thenReturn(Optional.of(existingRecord));

        attendanceEventService.processAttendanceEvent(event);

        verify(attendanceRecordRepository).saveAndFlush(existingRecord);
        assertEquals(originalCreatedAt, existingRecord.getCreatedAt());
        assertEquals(event.attendanceStatus(), existingRecord.getStatus());
        assertEquals(event.attendanceDate(), existingRecord.getDate());
        assertEquals(event.version(), existingRecord.getEventVersion());
        verify(processedEventService).markProcessed(
                event.eventId(),
                "ATTENDANCE_RECORDED"
        );
    }

    @Test
    void processAttendanceEvent_ignoresOlderVersionAndMarksEventProcessed() {
        AttendanceEvent incomingEvent = validEventWithVersion(4);
        UUID existingStudentId = UUID.randomUUID();
        UUID existingProgramId = UUID.randomUUID();
        UUID existingCohortId = UUID.randomUUID();
        LocalDateTime existingCreatedAt = LocalDateTime.of(2026, 10, 1, 8, 0);
        LocalDateTime existingUpdatedAt = LocalDateTime.of(2026, 10, 10, 9, 0);
        AttendanceRecord existingRecord = AttendanceRecord.builder()
                .attendanceId(incomingEvent.attendanceId())
                .studentId(existingStudentId)
                .programId(existingProgramId)
                .cohortId(existingCohortId)
                .status(AttendanceStatus.ABSENT)
                .date(LocalDate.of(2026, 10, 10))
                .createdAt(existingCreatedAt)
                .updatedAt(existingUpdatedAt)
                .eventVersion(5)
                .build();
        when(processedEventService.isProcessed(incomingEvent.eventId())).thenReturn(false);
        when(attendanceRecordRepository.findByIdForUpdate(incomingEvent.attendanceId()))
                .thenReturn(Optional.of(existingRecord));

        attendanceEventService.processAttendanceEvent(incomingEvent);

        verify(attendanceRecordRepository, never()).saveAndFlush(any(AttendanceRecord.class));
        assertEquals(incomingEvent.attendanceId(), existingRecord.getAttendanceId());
        assertEquals(existingStudentId, existingRecord.getStudentId());
        assertEquals(existingProgramId, existingRecord.getProgramId());
        assertEquals(existingCohortId, existingRecord.getCohortId());
        assertEquals(5, existingRecord.getEventVersion());
        assertEquals(AttendanceStatus.ABSENT, existingRecord.getStatus());
        assertEquals(LocalDate.of(2026, 10, 10), existingRecord.getDate());
        assertEquals(existingCreatedAt, existingRecord.getCreatedAt());
        assertEquals(existingUpdatedAt, existingRecord.getUpdatedAt());
        verify(processedEventService).markProcessed(
                incomingEvent.eventId(),
                "ATTENDANCE_RECORDED"
        );
    }

    @Test
    void processAttendanceEvent_ignoresEqualVersion() {
        AttendanceEvent incomingEvent = validEventWithVersion(5);
        UUID existingStudentId = UUID.randomUUID();
        UUID existingProgramId = UUID.randomUUID();
        UUID existingCohortId = UUID.randomUUID();
        LocalDate existingDate = LocalDate.of(2026, 10, 10);
        LocalDateTime existingCreatedAt = LocalDateTime.of(2026, 10, 1, 8, 0);
        LocalDateTime existingUpdatedAt = LocalDateTime.of(2026, 10, 10, 9, 0);
        AttendanceRecord existingRecord = AttendanceRecord.builder()
                .attendanceId(incomingEvent.attendanceId())
                .studentId(existingStudentId)
                .programId(existingProgramId)
                .cohortId(existingCohortId)
                .status(AttendanceStatus.ABSENT)
                .date(existingDate)
                .createdAt(existingCreatedAt)
                .updatedAt(existingUpdatedAt)
                .eventVersion(incomingEvent.version())
                .build();
        when(processedEventService.isProcessed(incomingEvent.eventId())).thenReturn(false);
        when(attendanceRecordRepository.findByIdForUpdate(incomingEvent.attendanceId()))
                .thenReturn(Optional.of(existingRecord));

        attendanceEventService.processAttendanceEvent(incomingEvent);

        verify(attendanceRecordRepository, never()).saveAndFlush(any(AttendanceRecord.class));
        assertEquals(incomingEvent.attendanceId(), existingRecord.getAttendanceId());
        assertEquals(existingStudentId, existingRecord.getStudentId());
        assertEquals(existingProgramId, existingRecord.getProgramId());
        assertEquals(existingCohortId, existingRecord.getCohortId());
        assertEquals(AttendanceStatus.ABSENT, existingRecord.getStatus());
        assertEquals(existingDate, existingRecord.getDate());
        assertEquals(existingCreatedAt, existingRecord.getCreatedAt());
        assertEquals(existingUpdatedAt, existingRecord.getUpdatedAt());
        assertEquals(incomingEvent.version(), existingRecord.getEventVersion());
        verify(processedEventService).markProcessed(
                incomingEvent.eventId(),
                "ATTENDANCE_RECORDED"
        );
    }

    @Test
    void processAttendanceEvent_updatesExistingRecordForNewerVersion() {
        AttendanceEvent incomingEvent = validEventWithVersion(6);
        LocalDateTime existingCreatedAt = LocalDateTime.of(2026, 10, 1, 8, 0);
        AttendanceRecord existingRecord = AttendanceRecord.builder()
                .attendanceId(incomingEvent.attendanceId())
                .createdAt(existingCreatedAt)
                .status(AttendanceStatus.ABSENT)
                .eventVersion(5)
                .build();
        when(processedEventService.isProcessed(incomingEvent.eventId())).thenReturn(false);
        when(attendanceRecordRepository.findByIdForUpdate(incomingEvent.attendanceId()))
                .thenReturn(Optional.of(existingRecord));

        attendanceEventService.processAttendanceEvent(incomingEvent);

        verify(attendanceRecordRepository).saveAndFlush(existingRecord);
        assertEquals(incomingEvent.attendanceId(), existingRecord.getAttendanceId());
        assertEquals(incomingEvent.studentId(), existingRecord.getStudentId());
        assertEquals(incomingEvent.programId(), existingRecord.getProgramId());
        assertEquals(incomingEvent.cohortId(), existingRecord.getCohortId());
        assertEquals(incomingEvent.attendanceStatus(), existingRecord.getStatus());
        assertEquals(incomingEvent.attendanceDate(), existingRecord.getDate());
        assertEquals(existingCreatedAt, existingRecord.getCreatedAt());
        assertEquals(
                LocalDateTime.ofInstant(incomingEvent.timestamp(), ZoneOffset.UTC),
                existingRecord.getUpdatedAt()
        );
        assertEquals(incomingEvent.version(), existingRecord.getEventVersion());
        verify(processedEventService).markProcessed(
                incomingEvent.eventId(),
                "ATTENDANCE_RECORDED"
        );
    }

    @Test
    void processAttendanceEvent_skipsDuplicateEvent() {
        AttendanceEvent event = validEvent();
        when(processedEventService.isProcessed(event.eventId())).thenReturn(true);

        attendanceEventService.processAttendanceEvent(event);

        verifyNoInteractions(attendanceRecordRepository);
        verify(processedEventService, never()).markProcessed(any(), anyString());
    }

    @Test
    void processAttendanceEvent_rejectsInvalidVersionBeforePersistence() {
        AttendanceEvent event = validEventWithVersion(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> attendanceEventService.processAttendanceEvent(event)
        );

        verifyNoInteractions(attendanceRecordRepository, processedEventService);
    }

    @Test
    void processAttendanceEvent_rejectsNullEventBeforePersistence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> attendanceEventService.processAttendanceEvent(null)
        );

        verifyNoInteractions(attendanceRecordRepository, processedEventService);
    }

    @ParameterizedTest(name = "rejects attendance event with missing {0}")
    @MethodSource("eventsWithMissingRequiredFields")
    void processAttendanceEvent_rejectsMissingRequiredField(
            String missingField,
            AttendanceEvent event
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> attendanceEventService.processAttendanceEvent(event),
                "Expected missing " + missingField + " to be rejected"
        );

        verifyNoInteractions(attendanceRecordRepository, processedEventService);
    }

    private static Stream<Arguments> eventsWithMissingRequiredFields() {
        return Stream.of(
                "attendanceId",
                "eventId",
                "studentId",
                "programId",
                "cohortId",
                "attendanceStatus",
                "attendanceDate",
                "timestamp"
        ).map(field -> Arguments.of(field, eventWithMissingRequiredField(field)));
    }

    private static AttendanceEvent eventWithMissingRequiredField(String field) {
        return new AttendanceEvent(
                "eventId".equals(field) ? null : UUID.randomUUID(),
                "attendanceId".equals(field) ? null : UUID.randomUUID(),
                "studentId".equals(field) ? null : UUID.randomUUID(),
                "programId".equals(field) ? null : UUID.randomUUID(),
                "cohortId".equals(field) ? null : UUID.randomUUID(),
                "attendanceStatus".equals(field) ? null : AttendanceStatus.PRESENT,
                "attendanceDate".equals(field) ? null : LocalDate.of(2026, 10, 12),
                "timestamp".equals(field) ? null : Instant.parse("2026-10-12T12:00:00Z"),
                1
        );
    }

    private AttendanceEvent validEvent() {
        return validEventWithVersion(1);
    }

    private AttendanceEvent validEventWithVersion(int version) {
        return new AttendanceEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                AttendanceStatus.PRESENT,
                LocalDate.of(2026, 10, 12),
                Instant.parse("2026-10-12T12:00:00Z"),
                version
        );
    }
}
