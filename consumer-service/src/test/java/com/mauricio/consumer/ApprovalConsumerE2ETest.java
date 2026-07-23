package com.mauricio.consumer;

import com.mauricio.consumer.messaging.ApprovalConsumer;
import com.mauricio.consumer.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "app.sqs.queue-name=document-approved-consumer-test",
        "app.consumer.poll-interval-ms=3600000"
})
class ApprovalConsumerE2ETest {

    @Autowired
    private SqsClient sqs;

    @Autowired
    @Qualifier("queueUrl")
    private String queueUrl;

    @Autowired
    private ApprovalConsumer consumer;

    @Autowired
    private ProcessedEventRepository processed;

    @Test
    @DisplayName("a message put on SQS is received, handled and recorded")
    void consumesFromSqs() {
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        sqs.sendMessage(b -> b
                .queueUrl(queueUrl)
                .messageBody("{\"documentId\":\"" + documentId + "\"}")
                .messageAttributes(Map.of(
                        "eventId", MessageAttributeValue.builder()
                                .dataType("String").stringValue(eventId.toString()).build())));

        consumer.poll();

        assertThat(processed.existsById(eventId))
                .as("the event was consumed from the queue and recorded")
                .isTrue();
    }
}
