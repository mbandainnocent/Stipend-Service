package com.stipend.service.kafka.consumer;

import com.stipend.service.Event.StudentStatusChangedEvent;
import com.stipend.service.service.StudentStatusChangedEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StudentStatusChangedEventConsumer {

    private static final Logger logger =
            LoggerFactory.getLogger(StudentStatusChangedEventConsumer.class);

    private final StudentStatusChangedEventService eventService;

    public StudentStatusChangedEventConsumer(
            StudentStatusChangedEventService eventService) {
        this.eventService = eventService;
    }

    @KafkaListener(
            id = "student-status-consumer",
            topics = "${app.kafka.topics.student-status}",
            groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type="
                    + "com.stipend.service.Event.StudentStatusChangedEvent"
    )
    public void consume(StudentStatusChangedEvent event) {
        try {
            eventService.process(event);
        } catch (RuntimeException e) {
            logger.error(
                    "Failed to process student status event: {}",
                    event == null ? null : event.eventId(),
                    e
            );
            throw e;
        }
    }
}
