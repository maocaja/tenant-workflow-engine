package com.mauricio.workflow.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentApproved(
        UUID documentId,
        UUID tenantId,
        String actor,
        Instant occurredAt
) implements DomainEvent {
}
