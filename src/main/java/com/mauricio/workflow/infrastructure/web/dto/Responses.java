package com.mauricio.workflow.infrastructure.web.dto;

import com.mauricio.workflow.domain.Document;
import com.mauricio.workflow.domain.DocumentStatus;
import com.mauricio.workflow.infrastructure.persistence.AuditEventEntity;
import com.mauricio.workflow.infrastructure.persistence.DocumentEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Responses {

    private Responses() {
    }

    public record DocumentResponse(
            UUID id,
            UUID tenantId,
            String reference,
            BigDecimal amount,
            String currency,
            DocumentStatus status,
            int signatures,
            Map<String, String> fields,
            Instant createdAt) {

        public static DocumentResponse from(Document d) {
            return new DocumentResponse(d.id(), d.tenantId(), d.reference(), d.amount(),
                    d.currency().getCurrencyCode(), d.status(), d.signatures(),
                    d.fields(), d.createdAt());
        }
    }

    public record AuditEventResponse(
            DocumentStatus from,
            DocumentStatus to,
            String actor,
            String reason,
            Instant occurredAt) {

        public static AuditEventResponse from(AuditEventEntity e) {
            return new AuditEventResponse(e.getFromStatus(), e.getToStatus(),
                    e.getActor(), e.getReason(), e.getOccurredAt());
        }
    }

    public record DocumentWithHistory(
            UUID id,
            UUID tenantId,
            String reference,
            DocumentStatus status,
            int signatures,
            List<AuditEventResponse> history) {

        public static DocumentWithHistory from(DocumentEntity d) {
            List<AuditEventResponse> history = d.getAuditEvents().stream()
                    .map(AuditEventResponse::from)
                    .toList();
            return new DocumentWithHistory(d.getId(), d.getTenantId(), d.getReference(),
                    d.getStatus(), d.getSignatures(), history);
        }
    }
}
