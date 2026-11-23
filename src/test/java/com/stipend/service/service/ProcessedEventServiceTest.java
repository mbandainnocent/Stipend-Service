package com.stipend.service.service;

import com.stipend.service.Model.ProcessedEvent;
import com.stipend.service.Repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProcessedEventServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private ProcessedEventService processedEventService;

    @Test
    void markProcessed_ShouldSaveStudentRegistrationEventRecord() {
        UUID eventId = UUID.randomUUID();

        processedEventService.markProcessed(eventId);

        ArgumentCaptor<ProcessedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(eventCaptor.capture());
        ProcessedEvent processedEvent = eventCaptor.getValue();
        assertEquals(eventId, processedEvent.getEventId());
        assertEquals("STUDENT_REGISTERED", processedEvent.getEventType());
        assertNotNull(processedEvent.getProcessedAt());
    }
}
