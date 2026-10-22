package com.stipend.service.service;

import com.stipend.service.Event.StudentStatusChangedEvent;
import com.stipend.service.Enum.StudentStatus;
import com.stipend.service.Exception.ResourceNotFoundException;
import com.stipend.service.Model.Student;
import com.stipend.service.Repository.StudentRegistrationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class StudentStatusChangedEventService {

    private static final String EVENT_TYPE = "STUDENT_STATUS_CHANGED";

    private final StudentStatusChangedEventValidator eventValidator;
    private final ProcessedEventService processedEventService;
    private final StudentRegistrationRepository studentRepository;

    public StudentStatusChangedEventService(
            StudentStatusChangedEventValidator eventValidator,
            ProcessedEventService processedEventService,
            StudentRegistrationRepository studentRepository) {
        this.eventValidator = eventValidator;
        this.processedEventService = processedEventService;
        this.studentRepository = studentRepository;
    }

    @Transactional
    public void process(StudentStatusChangedEvent event) {
        eventValidator.validate(event);

        if (processedEventService.isProcessed(event.eventId())) {
            log.info("Student status event {} already processed", event.eventId());
            return;
        }

        Student student = studentRepository.findById(event.studentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found: " + event.studentId()
                ));

        if (event.version() <= student.getStatusVersion()) {
            log.info(
                    "Ignoring stale student status event {} at version {} for student {} "
                            + "with current status version {}",
                    event.eventId(),
                    event.version(),
                    event.studentId(),
                    student.getStatusVersion()
            );
        } else {
            student.setStatus(event.status());
            student.setStipendEligible(
                    event.status() == StudentStatus.ACTIVE
                            && student.isCohortStipendEligible()
            );
            student.setStatusVersion(event.version());
            studentRepository.save(student);
        }

        processedEventService.markProcessed(event.eventId(), EVENT_TYPE);
    }
}
