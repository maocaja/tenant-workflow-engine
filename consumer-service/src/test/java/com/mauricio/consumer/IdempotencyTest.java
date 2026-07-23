package com.mauricio.consumer;

import com.mauricio.consumer.messaging.ApprovalConsumer;
import com.mauricio.consumer.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "app.sqs.queue-name=document-approved-consumer-test",
        "app.consumer.poll-interval-ms=3600000"
})
class IdempotencyTest {

    @Autowired
    private ApprovalConsumer consumer;

    @Autowired
    private ProcessedEventRepository processed;

    @Test
    @DisplayName("the same event handled twice produces a single effect")
    void sameEventHandledOnce() {
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        String body = "{\"documentId\":\"" + documentId + "\"}";

        boolean firstTime = consumer.handle(eventId, body);
        boolean secondTime = consumer.handle(eventId, body);

        assertThat(firstTime).as("first delivery does the work").isTrue();
        assertThat(secondTime).as("redelivery is a no-op").isFalse();
        assertThat(processed.findAll().stream()
                .filter(e -> e.getEventId().equals(eventId))
                .count())
                .isEqualTo(1);
    }
}
