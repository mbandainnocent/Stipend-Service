package com.stipend.service.service;

import com.stipend.service.Model.ProcessedEvent;
import com.stipend.service.Repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProcessedEventService {

    private static final String EVENT_TYPE = "STUDENT_REGISTERED";

    private final ProcessedEventRepository processedEventRepository;

    public ProcessedEventService(
            ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    public boolean isProcessed(UUID eventId) {
        return processedEventRepository.existsById(eventId);
    }

    public void markProcessed(UUID eventId) {
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(eventId)
                .eventType(EVENT_TYPE)
                .processedAt(Instant.now())
                .build();

        processedEventRepository.save(processedEvent);
    }
}
