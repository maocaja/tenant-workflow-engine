package com.mauricio.workflow.application.port;

import com.mauricio.workflow.domain.Rule;

import java.util.List;
import java.util.UUID;

public interface TenantRules {

    List<Rule> forTenant(UUID tenantId);
}
