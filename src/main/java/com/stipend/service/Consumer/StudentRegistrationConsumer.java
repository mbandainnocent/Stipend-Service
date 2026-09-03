package com.stipend.service.Consumer;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.service.StudentEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StudentRegistrationConsumer {

    private  final StudentEventService studentEventService;

    @KafkaListener(topics = "student-registration",
                          groupId = "stipend-service")

    public void consumeStudentRegistration(StudentRegisteredEvent registeredEvent) {
        System.out.println("Received student registration event {}: " + registeredEvent);

        studentEventService.processStudentRegistrationEvent(registeredEvent);
    }


}

