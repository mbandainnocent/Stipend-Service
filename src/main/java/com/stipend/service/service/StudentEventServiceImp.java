package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.Model.ProcessedEvent;
import com.stipend.service.Model.StipendStudent;
import com.stipend.service.Repository.ProcessedEventRepository;
import com.stipend.service.Repository.StipendStudentRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;


@Service
@RequiredArgsConstructor
@Slf4j
public class StudentEventServiceImp implements StudentEventService{

    private static final String EVENT_TYPE = "STUDENT_REGISTERED";

    private final ProcessedEventRepository processedEventRepository;
    private final StipendStudentRepository stipendStudentRepository;
    private final ProcessedEventService processedEventService;

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
                .processedAt(java.time.Instant.now())

                .build();
        processedEventService.saveProcessedEvent(processedEvent);

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
        student.setCohortStartDate(event.cohortStartDate() != null ? 
            event.cohortStartDate().atZone(java.time.ZoneId.systemDefault()).toLocalDate() : null);
        student.setCohortEndDate(event.cohortEndDate() != null ? 
            event.cohortEndDate().atZone(java.time.ZoneId.systemDefault()).toLocalDate() : null);
        student.setRegisteredAt(event.registrationDate());
        student.setActive(true);
        student.setUpdatedAt(java.time.Instant.now());


    }

    private void validateEvent(StudentRegisteredEvent event) {
        if (event == null){
            throw new IllegalArgumentException("student registration event cannot be null");
        }
        if (event.eventId() == null){
            throw new IllegalArgumentException("eventId is required");
        }
        if (event.studentId() == null){
            throw new IllegalArgumentException("Student ID cannot be null");
        }
    }

}
