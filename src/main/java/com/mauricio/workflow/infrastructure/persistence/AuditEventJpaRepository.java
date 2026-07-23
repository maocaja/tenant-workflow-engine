package com.mauricio.workflow.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, Long> {

    List<AuditEventEntity> findByDocumentIdOrderByOccurredAtAsc(UUID documentId);
}
