package com.stipend.service.kafka.consumer;

import com.stipend.service.Event.AttendanceEvent;
import com.stipend.service.service.AttendanceEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AttendanceEventConsumer {
    private final AttendanceEventService attendanceEventService;

    public AttendanceEventConsumer(AttendanceEventService attendanceEventService) {
        this.attendanceEventService = attendanceEventService;
    }

    private final Logger logger = LoggerFactory.getLogger(AttendanceEventConsumer.class);

    @KafkaListener(id = "AttendanceEvent",
    topics = "${app.kafka.topics.attendance}",
    groupId = "${spring.kafka.consumer.group-id}",
    properties = "spring.json.value.default.type=com.stipend.service.Event.AttendanceEvent")
    public void consume(AttendanceEvent event) {

        try {
            attendanceEventService.processAttendanceEvent(event);

        } catch (Exception e) {
            logger.error(
                    "Failed to process attendance event: {}",
                    event == null ? null : event.eventId(),
                    e
            );
            throw new RuntimeException(
                    "Failed to process attendance event "
                            + (event == null ? null : event.eventId()),
                    e
            );
        }

    }

}
