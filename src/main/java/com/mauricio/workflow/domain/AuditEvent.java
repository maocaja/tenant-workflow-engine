package com.mauricio.workflow.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID documentId,
        DocumentStatus from,
        DocumentStatus to,
        String actor,
        String reason,
        Instant occurredAt
) {
    public AuditEvent {
        if (from == to) {
            throw new IllegalArgumentException(
                    "From and to must be different"
            );
        }

        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException(
                    "actor cannot be null or blank"
            );
        }

    }




}
