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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
        when(attendanceRecordRepository.findById(event.attendanceId()))
                .thenReturn(Optional.empty());

        attendanceEventService.processAttendanceEvent(event);

        ArgumentCaptor<AttendanceRecord> recordCaptor =
                ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRecordRepository).save(recordCaptor.capture());
        AttendanceRecord savedRecord = recordCaptor.getValue();
        assertEquals(event.attendanceId(), savedRecord.getAttendanceId());
        assertEquals(event.studentId(), savedRecord.getStudentId());
        assertEquals(event.programId(), savedRecord.getProgramId());
        assertEquals(event.cohortId(), savedRecord.getCohortId());
        assertEquals(event.attendanceStatus(), savedRecord.getStatus());
        assertEquals(event.attendanceDate(), savedRecord.getDate());
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
        when(attendanceRecordRepository.findById(event.attendanceId()))
                .thenReturn(Optional.of(existingRecord));

        attendanceEventService.processAttendanceEvent(event);

        verify(attendanceRecordRepository).save(existingRecord);
        assertEquals(originalCreatedAt, existingRecord.getCreatedAt());
        assertEquals(event.attendanceStatus(), existingRecord.getStatus());
        assertEquals(event.attendanceDate(), existingRecord.getDate());
        verify(processedEventService).markProcessed(
                event.eventId(),
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
