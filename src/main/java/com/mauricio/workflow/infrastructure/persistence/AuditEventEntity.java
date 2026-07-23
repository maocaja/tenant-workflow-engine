package com.mauricio.workflow.infrastructure.persistence;

import com.mauricio.workflow.domain.DocumentStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private DocumentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private DocumentStatus toStatus;

    @Column(nullable = false)
    private String actor;

    @Column
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(DocumentEntity document, DocumentStatus fromStatus,
                            DocumentStatus toStatus, String actor, String reason,
                            Instant occurredAt) {
        this.document = document;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public DocumentEntity getDocument() {
        return document;
    }

    public DocumentStatus getFromStatus() {
        return fromStatus;
    }

    public DocumentStatus getToStatus() {
        return toStatus;
    }

    public String getActor() {
        return actor;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
