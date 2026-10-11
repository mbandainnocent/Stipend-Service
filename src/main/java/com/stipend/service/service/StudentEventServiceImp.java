package com.stipend.service.service;

import com.stipend.service.Event.StudentRegisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class StudentEventServiceImp implements StudentEventService {

    private final StudentRegistrationEventValidator eventValidator;
    private final ProcessedEventService processedEventService;
    private final StudentRegistrationService studentRegistrationService;

    public StudentEventServiceImp(
            StudentRegistrationEventValidator eventValidator,
            ProcessedEventService processedEventService,
            StudentRegistrationService studentRegistrationService) {
        this.eventValidator = eventValidator;
        this.processedEventService = processedEventService;
        this.studentRegistrationService = studentRegistrationService;
    }

    @Override
    @Transactional
    public void processStudentRegistrationEvent(StudentRegisteredEvent event) {
        eventValidator.validate(event);

        if (processedEventService.isProcessed(event.eventId())) {
            log.info(
                    "Student registration event {} already processed",
                    event.eventId()
            );
            return;
        }

        studentRegistrationService.register(event);
        processedEventService.markProcessed(event.eventId());

        log.info(
                "Student registration event {} processed successfully for student {}",
                event.eventId(),
                event.studentId()
        );
    }
}
