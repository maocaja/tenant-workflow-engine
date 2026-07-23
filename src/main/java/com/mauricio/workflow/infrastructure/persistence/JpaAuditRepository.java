package com.mauricio.workflow.infrastructure.persistence;

import com.mauricio.workflow.application.port.AuditRepository;
import com.mauricio.workflow.domain.AuditEvent;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAuditRepository implements AuditRepository {

    private final AuditEventJpaRepository jpa;
    private final DocumentJpaRepository documents;

    public JpaAuditRepository(AuditEventJpaRepository jpa, DocumentJpaRepository documents) {
        this.jpa = jpa;
        this.documents = documents;
    }

    @Override
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
