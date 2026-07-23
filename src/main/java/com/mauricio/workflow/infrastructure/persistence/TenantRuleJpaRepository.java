package com.mauricio.workflow.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantRuleJpaRepository extends JpaRepository<TenantRuleEntity, UUID> {

    List<TenantRuleEntity> findByTenantIdAndGate(UUID tenantId, String gate);
}
