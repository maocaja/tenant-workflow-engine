package com.mauricio.workflow.infrastructure.messaging;

import com.mauricio.workflow.domain.Document;
import com.mauricio.workflow.infrastructure.DocumentWorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "app.sqs.enabled=true",
        "app.sqs.endpoint=http://localhost:4566",
        "app.sqs.queue-name=document-approved-test",
        "app.sqs.dlq-name=document-approved-test-dlq",
        "app.outbox.relay.interval-ms=3600000"
})
class OutboxRelayTest {

    private static final UUID CLIENT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Currency COP = Currency.getInstance("COP");

    @Autowired
    private DocumentWorkflowService workflow;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private SqsClient sqs;

    @Autowired
    private QueueRegistry queues;

    @Test
    @DisplayName("the relay ships an approval event from the outbox to SQS")
    void relayShipsApprovalToSqs() {
        Document draft = workflow.create(CLIENT_B, "FAC-RELAY",
                new BigDecimal("1000000"), COP, Map.of());
        workflow.submit(draft.id(), "ana");
        workflow.sign(draft.id());
        workflow.sign(draft.id());
        workflow.approve(draft.id(), "beto");

        int shipped = relay.shipBatch();
        assertThat(shipped).isGreaterThanOrEqualTo(1);

        List<Message> messages = sqs.receiveMessage(b -> b
                .queueUrl(queues.mainQueueUrl())
                .maxNumberOfMessages(10)
                .waitTimeSeconds(2)).messages();

        assertThat(messages)
                .as("the approval for our document reached the queue")
                .anyMatch(m -> m.body().contains(draft.id().toString()));
    }
}
