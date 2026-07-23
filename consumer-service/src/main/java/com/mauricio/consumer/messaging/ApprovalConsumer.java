package com.mauricio.consumer.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mauricio.consumer.persistence.ProcessedEventEntity;
import com.mauricio.consumer.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Polls SQS and handles each approval event exactly-once in effect, even though
 * delivery is at-least-once. Idempotency key is the event id carried as a message
 * attribute; a handled id is recorded in processed_events, and a re-delivery of the
 * same id is a no-op. A message is only deleted after it is handled — if handling
 * throws, the message is left on the queue, redelivered, and eventually sent to the
 * dead-letter queue by the redrive policy.
 */
public class ApprovalConsumer {

    private static final Logger log = LoggerFactory.getLogger(ApprovalConsumer.class);

    private final SqsClient sqs;
    private final String queueUrl;
    private final ProcessedEventRepository processed;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApprovalConsumer(SqsClient sqs, String queueUrl,
                            ProcessedEventRepository processed,
                            ObjectMapper objectMapper, Clock clock) {
        this.sqs = sqs;
        this.queueUrl = queueUrl;
        this.processed = processed;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.consumer.poll-interval-ms:1000}")
    public void poll() {
        List<Message> messages = sqs.receiveMessage(b -> b
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(1)
                .messageAttributeNames("All")).messages();

        for (Message message : messages) {
            try {
                UUID eventId = UUID.fromString(
                        message.messageAttributes().get("eventId").stringValue());
                handle(eventId, message.body());
                sqs.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            } catch (RuntimeException e) {
                // leave the message on the queue: it will be redelivered and,
                // after maxReceiveCount, moved to the dead-letter queue.
                log.warn("handling failed, leaving message for redelivery: {}", e.getMessage());
            }
        }
    }

    @Transactional
    public boolean handle(UUID eventId, String body) {
        if (processed.existsById(eventId)) {
            return false;
        }
        UUID documentId = extractDocumentId(body);
        processed.save(new ProcessedEventEntity(eventId, documentId, clock.instant()));
        log.info("processed approval event {} for document {}", eventId, documentId);
        return true;
    }

    private UUID extractDocumentId(String body) {
        try {
            return UUID.fromString(objectMapper.readTree(body).get("documentId").asText());
        } catch (Exception e) {
            throw new IllegalArgumentException("cannot read documentId from event body", e);
        }
    }
}
