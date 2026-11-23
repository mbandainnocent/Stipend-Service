package com.stipend.service.Model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Schema(description = "Represents a Kafka event that has been processed by the stipend service")
public class ProcessedEvent {
    @Id
    @Schema(description = "Unique identifier for the event, provided by Kafka source system")
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Schema(description = "Type of the event that was processed")
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Schema(description = "Timestamp when the event was processed")
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
