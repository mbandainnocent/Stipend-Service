package com.stipend.service.KafkaConsumer;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.service.StudentEventService;
import com.stipend.service.service.StudentEventServiceImp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class StudentRegistrationEventConsumerTest {


    @Mock
    private StudentEventServiceImp studentEventServiceImp;

    private StudentRegistrationEventConsumer studentRegistrationEventConsumer;

    @BeforeEach
    void setUp() {
        studentRegistrationEventConsumer = new StudentRegistrationEventConsumer(studentEventServiceImp);
    }


    @Test
    void shouldConsumeStudentRegistrationEvent() {
        // Arrange
        StudentRegisteredEvent event = new StudentRegisteredEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "John",
                "Doe",
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                Instant.now(),
                1
        );

        // Act
        studentRegistrationEventConsumer.consume(event);

        // Assert
        Mockito.verify(studentEventServiceImp)
                .processStudentRegistrationEvent(event);
    }

    @Test
    void shouldLogErrorAndRethrowWhenProcessingFails() {
        // Arrange
        StudentRegisteredEvent event = new StudentRegisteredEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "John",
                "Doe",
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(86400),
                Instant.now(),
                1
        );

        RuntimeException expectedException = new RuntimeException("Processing failed");
        Mockito.doThrow(expectedException)
                .when(studentEventServiceImp)
                .processStudentRegistrationEvent(event);

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            studentRegistrationEventConsumer.consume(event);
        });

        assertEquals("Processing failed", thrown.getMessage());
        Mockito.verify(studentEventServiceImp)
                .processStudentRegistrationEvent(event);
    }



}