package com.mauricio.workflow.infrastructure.persistence;

import com.mauricio.workflow.application.port.AuditRepository;
import com.mauricio.workflow.domain.AuditEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaAuditRepository implements AuditRepository {

    private final AuditEventJpaRepository jpa;
    private final DocumentJpaRepository documents;

    public JpaAuditRepository(AuditEventJpaRepository jpa, DocumentJpaRepository documents) {
        this.jpa = jpa;
        this.documents = documents;
    }

    // REQUIRES_NEW: the audit commits in its own transaction, suspended from the caller's.
    // An append-only audit trail must record that a decision was made even if the business
    // transaction that triggered it later rolls back.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditEvent event) {
        DocumentEntity ref = documents.getReferenceById(event.documentId());
        jpa.save(new AuditEventEntity(
                ref,
                event.from(),
                event.to(),
                event.actor(),
                event.reason(),
                event.occurredAt()));
    }
}
