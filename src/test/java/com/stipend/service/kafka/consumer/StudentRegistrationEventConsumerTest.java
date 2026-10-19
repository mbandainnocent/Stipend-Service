package com.stipend.service.kafka.consumer;

import com.stipend.service.Event.StudentRegisteredEvent;
import com.stipend.service.service.StudentEventService;
import com.stipend.service.service.StudentRegistrationEventValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
@ExtendWith(MockitoExtension.class)
class StudentRegistrationEventConsumerTest {


    @Mock
    private StudentEventService studentEventService;

    private StudentRegistrationEventConsumer studentRegistrationEventConsumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        studentRegistrationEventConsumer = new StudentRegistrationEventConsumer(studentEventService);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void shouldDeserializeAndConsumeRegistrationJsonPayload() throws Exception {
        String json = """
                {
                  "eventId": "a1234567-1234-1234-1234-123456789001",
                  "studentId": "b1234567-1234-1234-1234-123456789001",
                  "firstName": "John",
                  "lastName": "Doe",
                  "programId": "c1234567-1234-1234-1234-123456789001",
                  "cohortId": "d1234567-1234-1234-1234-123456789001",
                  "registeredAt": "2026-10-14T15:00:00Z",
                  "cohortStartDate": "2026-10-01T00:00:00Z",
                  "cohortEndDate": "2027-01-31T23:59:59Z",
                  "registrationDate": "2026-10-14T00:00:00Z",
                  "stipendEligible": true,
                  "monthlyStipendAmount": 50000.00,
                  "stipendCurrency": "RWF",
                  "version": 1
                }
                """;

        StudentRegisteredEvent event =
                objectMapper.readValue(json, StudentRegisteredEvent.class);

        new StudentRegistrationEventValidator().validate(event);
        studentRegistrationEventConsumer.consume(event);

        assertEquals(
                UUID.fromString("a1234567-1234-1234-1234-123456789001"),
                event.eventId()
        );
        assertEquals(
                UUID.fromString("b1234567-1234-1234-1234-123456789001"),
                event.studentId()
        );
        assertTrue(event.stipendEligible());
        assertEquals(new BigDecimal("50000.00"), event.monthlyStipendAmount());
        assertEquals("RWF", event.stipendCurrency());
        Mockito.verify(studentEventService).processStudentRegistrationEvent(event);
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
                true,
                new BigDecimal("50000.00"),
                "RWF",
                1
        );

        // Act
        studentRegistrationEventConsumer.consume(event);

        // Assert
        Mockito.verify(studentEventService)
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
                true,
                new BigDecimal("50000.00"),
                "RWF",
                1
        );

        RuntimeException expectedException = new RuntimeException("Processing failed");
        Mockito.doThrow(expectedException)
                .when(studentEventService)
                .processStudentRegistrationEvent(event);

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            studentRegistrationEventConsumer.consume(event);
        });

        assertEquals("Processing failed", thrown.getMessage());
        assertSame(expectedException, thrown);
        Mockito.verify(studentEventService)
                .processStudentRegistrationEvent(event);
    }



}