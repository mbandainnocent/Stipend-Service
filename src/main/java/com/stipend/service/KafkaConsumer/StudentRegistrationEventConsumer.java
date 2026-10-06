package com.stipend.service.KafkaConsumer;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.service.StudentEventServiceImp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StudentRegistrationEventConsumer {

    private final StudentEventServiceImp studentEventServiceImp;
    public StudentRegistrationEventConsumer(StudentEventServiceImp studentEventServiceImp) {
        this.studentEventServiceImp = studentEventServiceImp;
    }
    private final Logger logger = LoggerFactory.getLogger(StudentRegistrationEventConsumer.class);



    @KafkaListener(  id = "registration-consumer",
            topics = "${app.kafka.topics.registration}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(StudentRegisteredEvent event) {
       try {
           studentEventServiceImp.processStudentRegistrationEvent(event);
       } catch (Exception e) {
           logger.error("Failed to process registration event: {}", event.eventId(), e);
           throw e; // Re-throw for Kafka retry/DLT handling
       }
    }
}
