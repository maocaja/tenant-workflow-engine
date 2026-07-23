package com.mauricio.workflow.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    // Fetch every document and its audit history in a single statement.
    // distinct de-duplicates the roots multiplied by the left join.
    @Query("select distinct d from DocumentEntity d left join fetch d.auditEvents")
    List<DocumentEntity> findAllWithAudit();
}
