package com.mauricio.workflow.infrastructure;

import com.mauricio.workflow.domain.Document;
import com.mauricio.workflow.infrastructure.persistence.OutboxEntity;
import com.mauricio.workflow.infrastructure.persistence.OutboxJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxWriteTest {

    private static final UUID CLIENT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Currency COP = Currency.getInstance("COP");

    @Autowired
    private DocumentWorkflowService workflow;

    @Autowired
    private OutboxJpaRepository outbox;

    @Test
    @DisplayName("approving writes a DocumentApproved row to the outbox, unpublished")
    void approvalWritesOutboxRow() {
        Document draft = workflow.create(CLIENT_B, "FAC-OUT",
                new BigDecimal("1000000"), COP, Map.of());
        workflow.submit(draft.id(), "ana");
        workflow.sign(draft.id());
        workflow.sign(draft.id());
        workflow.approve(draft.id(), "beto");

        List<OutboxEntity> rows = outbox.findByAggregateId(draft.id());

        assertThat(rows).hasSize(1);
        OutboxEntity row = rows.get(0);
        assertThat(row.getType()).isEqualTo("DocumentApproved");
        assertThat(row.getPublishedAt()).as("not yet shipped by the relay").isNull();
        assertThat(row.getPayload()).contains(draft.id().toString());
    }
}
