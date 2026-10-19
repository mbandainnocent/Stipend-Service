package com.stipend.service.kafka.consumer;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.service.StudentEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StudentRegistrationEventConsumer {

    private final StudentEventService studentEventService;

    public StudentRegistrationEventConsumer(StudentEventService studentEventService) {
        this.studentEventService = studentEventService;
    }

    private final Logger logger = LoggerFactory.getLogger(StudentRegistrationEventConsumer.class);

    @KafkaListener(  id = "registration-consumer",
            topics = "${app.kafka.topics.registration}",
            containerFactory = "studentRegistrationKafkaListenerContainerFactory")
    public void consume(StudentRegisteredEvent event) {
       try {
           studentEventService.processStudentRegistrationEvent(event);
       } catch (RuntimeException e) {
           logger.error(
                   "Failed to process registration event: {}",
                   event == null ? null : event.eventId(),
                   e
           );
           throw e;
       }
    }
}
