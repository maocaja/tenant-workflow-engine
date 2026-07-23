package com.mauricio.consumer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
    }

    public ProcessedEventEntity(UUID eventId, UUID documentId, Instant processedAt) {
        this.eventId = eventId;
        this.documentId = documentId;
        this.processedAt = processedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
