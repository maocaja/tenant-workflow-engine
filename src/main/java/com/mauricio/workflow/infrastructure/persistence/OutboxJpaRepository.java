package com.mauricio.workflow.infrastructure.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {

    List<OutboxEntity> findByPublishedAtIsNullOrderByCreatedAtAsc(Limit limit);

    List<OutboxEntity> findByAggregateId(UUID aggregateId);
}
