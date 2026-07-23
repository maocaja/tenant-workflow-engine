package com.mauricio.workflow.infrastructure;

import com.mauricio.workflow.application.port.AuditRepository;
import com.mauricio.workflow.application.port.DocumentRepository;
import com.mauricio.workflow.domain.AuditEvent;
import com.mauricio.workflow.domain.Document;
import com.mauricio.workflow.domain.DocumentStatus;
import com.mauricio.workflow.infrastructure.persistence.AuditEventJpaRepository;
import com.mauricio.workflow.infrastructure.persistence.DocumentJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AuditSurvivesRollbackTest {

    private static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Currency COP = Currency.getInstance("COP");

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private AuditRepository audit;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private DocumentJpaRepository documentJpa;

    @Autowired
    private AuditEventJpaRepository auditJpa;

    @Test
    @DisplayName("when the business transaction rolls back, the document change reverts but the audit survives")
    void auditSurvivesBusinessRollback() {
        Document draft = new Document(UUID.randomUUID(), TENANT, "FAC-RB",
                new BigDecimal("100"), COP, DocumentStatus.DRAFT, 0, Map.of(), Instant.now());
        documents.save(draft);

        int auditRowsBefore = auditJpa.findByDocumentIdOrderByOccurredAtAsc(draft.id()).size();

        TransactionTemplate tx = new TransactionTemplate(txManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            audit.save(new AuditEvent(draft.id(), DocumentStatus.DRAFT,
                    DocumentStatus.SUBMITTED, "ana", "attempt", Instant.now()));
            documents.save(draft.withStatus(DocumentStatus.SUBMITTED));
            throw new RuntimeException("boom after audit");
        })).isInstanceOf(RuntimeException.class);

        // the document change lived in the outer transaction -> reverted
        assertThat(documentJpa.findById(draft.id()))
                .get()
                .extracting(e -> e.getStatus())
                .isEqualTo(DocumentStatus.DRAFT);

        // the audit committed in its own REQUIRES_NEW transaction -> survived
        assertThat(auditJpa.findByDocumentIdOrderByOccurredAtAsc(draft.id()))
                .hasSize(auditRowsBefore + 1);
    }
}
