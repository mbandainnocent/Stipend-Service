package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.Model.ProcessedEvent;
import com.stipend.service.Model.StipendStudent;
import com.stipend.service.Repository.ProcessedEventRepository;
import com.stipend.service.Repository.StipendStudentRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;

import java.time.Instant;
import java.time.ZoneId;


@Service
@Slf4j
public class StudentEventServiceImp implements StudentEventService {

    private static final String EVENT_TYPE = "STUDENT_REGISTERED";

    private final ProcessedEventRepository processedEventRepository;
    private final StipendStudentRepository stipendStudentRepository;
    private final ZoneId cohortDateZone;

    public StudentEventServiceImp(
            ProcessedEventRepository processedEventRepository,
            StipendStudentRepository stipendStudentRepository,
            @Value("${app.registration.business-time-zone:Africa/Kigali}") String businessTimeZone) {
        this.processedEventRepository = processedEventRepository;
        this.stipendStudentRepository = stipendStudentRepository;
        this.cohortDateZone = ZoneId.of(businessTimeZone);
    }

    @Override
    @EventListener
    @Transactional
    public void processStudentRegistrationEvent(StudentRegisteredEvent event) {
        validateEvent(event);

        if (processedEventRepository.existsById(event.eventId())){
            log.info("student registration event {} already processed", event.eventId());
            return;
        }

        StipendStudent student = stipendStudentRepository.findById(event.studentId())
                .orElseGet(() -> createStudent(event));
        
        updateStudent(student, event);
        stipendStudentRepository.save(student);

        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.eventId())
                .eventType(EVENT_TYPE)
                .processedAt(Instant.now())

                .build();
        processedEventRepository.saveAndFlush(processedEvent);

        log.info("student registration event {} " +
                "processed successfully",
                event.eventId());


    }

    private StipendStudent createStudent(StudentRegisteredEvent event) {
        return StipendStudent.builder()

                .studentId(event.studentId())
                .active(true)
                .build();
    }

    private void updateStudent(StipendStudent student,
                               StudentRegisteredEvent event) {
        student.setStudentId(event.studentId());
        student.setFirstName(event.firstName());
        student.setLastName(event.lastName());
        student.setProgramId(event.programId());
        student.setCohortId(event.cohortId());
        student.setCohortStartDate(event.cohortStartDate().atZone(cohortDateZone).toLocalDate());
        student.setCohortEndDate(event.cohortEndDate().atZone(cohortDateZone).toLocalDate());
        student.setRegisteredAt(event.registeredAt());
        student.setActive(true);
        student.setUpdatedAt(Instant.now());
    }

    private void validateEvent(StudentRegisteredEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("student registration event cannot be null");
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
            throw new IllegalArgumentException("cohortEndDate must not be before cohortStartDate");
        }
        if (event.registeredAt() == null) {
            throw new IllegalArgumentException("registeredAt is required");
        }
    }

}
