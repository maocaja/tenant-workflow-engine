package com.mauricio.workflow.infrastructure.messaging;

import com.mauricio.workflow.infrastructure.persistence.OutboxEntity;
import com.mauricio.workflow.infrastructure.persistence.OutboxJpaRepository;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Ships unpublished outbox rows to SQS, then marks them published. The send
 * happens before the mark, so a crash in between leaves the row unpublished and
 * it is retried on the next run — at-least-once delivery. Consumers must be
 * idempotent.
 */
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;

    private final OutboxJpaRepository outbox;
    private final SqsClient sqs;
    private final QueueRegistry queues;
    private final Clock clock;

    public OutboxRelay(OutboxJpaRepository outbox, SqsClient sqs, QueueRegistry queues, Clock clock) {
        this.outbox = outbox;
        this.sqs = sqs;
        this.queues = queues;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.interval-ms:2000}")
    @Transactional
    public int shipBatch() {
        List<OutboxEntity> rows =
                outbox.findByPublishedAtIsNullOrderByCreatedAtAsc(Limit.of(BATCH_SIZE));

        for (OutboxEntity row : rows) {
            sqs.sendMessage(b -> b
                    .queueUrl(queues.mainQueueUrl())
                    .messageBody(row.getPayload())
                    .messageAttributes(Map.of(
                            "type", MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(row.getType())
                                    .build(),
                            "eventId", MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(row.getId().toString())
                                    .build())));
            row.markPublished(clock.instant());
        }
        return rows.size();
    }
}
