package com.stipend.service.service;

import com.stipend.service.Event.AttendanceEvent;
import com.stipend.service.Model.AttendanceRecord;
import com.stipend.service.Repository.AttendanceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class AttendanceEventServiceImpl implements AttendanceEventService {

    private static final String EVENT_TYPE = "ATTENDANCE_RECORDED";
    private static final Logger logger =
            LoggerFactory.getLogger(AttendanceEventServiceImpl.class);

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ProcessedEventService processedEventService;

    public AttendanceEventServiceImpl(
            AttendanceRecordRepository attendanceRecordRepository,
            ProcessedEventService processedEventService) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.processedEventService = processedEventService;
    }

    @Override
    @Transactional
    public void processAttendanceEvent(AttendanceEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Attendance event cannot be null");
        }

        if (event.attendanceId() == null) {
            throw new IllegalArgumentException("attendanceId is required");
        }

        if (event.eventId() == null
                || event.studentId() == null
                || event.programId() == null
                || event.cohortId() == null
                || event.attendanceStatus() == null
                || event.attendanceDate() == null
                || event.timestamp() == null) {
            throw new IllegalArgumentException(
                    "Attendance event is missing one or more required fields"
            );
        }

        if (event.version() < 1) {
            throw new IllegalArgumentException(
                    "version must be greater than or equal to 1"
            );
        }

        if (processedEventService.isProcessed(event.eventId())) {
            logger.info("Attendance event {} already processed", event.eventId());
            return;
        }

        LocalDateTime eventTime =
                LocalDateTime.ofInstant(event.timestamp(), ZoneOffset.UTC);

        AttendanceRecord record = attendanceRecordRepository
                .findByIdForUpdate(event.attendanceId())
                .orElseGet(() -> AttendanceRecord.builder()
                        .attendanceId(event.attendanceId())
                        .createdAt(eventTime)
                        .build());

        if (event.version() <= record.getEventVersion()) {
            logger.info(
                    "Ignoring stale attendance event {} at version {} for attendance {} "
                            + "with current version {}",
                    event.eventId(),
                    event.version(),
                    event.attendanceId(),
                    record.getEventVersion()
            );
            processedEventService.markProcessed(event.eventId(), EVENT_TYPE);
            return;
        }

        record.setStudentId(event.studentId());
        record.setProgramId(event.programId());
        record.setCohortId(event.cohortId());
        record.setStatus(event.attendanceStatus());
        record.setDate(event.attendanceDate());
        record.setUpdatedAt(eventTime);
        record.setEventVersion(event.version());

        attendanceRecordRepository.saveAndFlush(record);
        processedEventService.markProcessed(event.eventId(), EVENT_TYPE);
    }
}
