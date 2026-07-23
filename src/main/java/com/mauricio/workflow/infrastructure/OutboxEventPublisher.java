package com.mauricio.workflow.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mauricio.workflow.application.port.EventPublisher;
import com.mauricio.workflow.domain.DocumentApproved;
import com.mauricio.workflow.domain.DomainEvent;
import com.mauricio.workflow.infrastructure.persistence.OutboxEntity;
import com.mauricio.workflow.infrastructure.persistence.OutboxJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Writes the event to the outbox table in the caller's transaction (default
 * REQUIRED). Because the row lands atomically with the business change, there is
 * no dual-write gap: either both commit or neither does. A separate relay ships
 * the row to the broker later.
 */
@Component
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxJpaRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxEventPublisher(OutboxJpaRepository outbox, ObjectMapper objectMapper, Clock clock) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publish(DomainEvent event) {
        UUID aggregateId = aggregateIdOf(event);
        outbox.save(new OutboxEntity(
                UUID.randomUUID(),
                aggregateId,
                event.getClass().getSimpleName(),
                serialize(event),
                clock.instant()));
    }

    private static UUID aggregateIdOf(DomainEvent event) {
        return switch (event) {
            case DocumentApproved e -> e.documentId();
        };
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize event " + event, e);
        }
    }
}
