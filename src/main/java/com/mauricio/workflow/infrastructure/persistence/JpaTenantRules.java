package com.mauricio.workflow.infrastructure.persistence;

import com.mauricio.workflow.application.port.TenantRules;
import com.mauricio.workflow.domain.*;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Repository
public class JpaTenantRules implements TenantRules {

    private final TenantRuleJpaRepository jpa;

    public JpaTenantRules(TenantRuleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Rule> forTenant(UUID tenantId, Gate gate) {
        return jpa.findByTenantIdAndGate(tenantId, gate.name())
                .stream()
                .map(JpaTenantRules::toRule)
                .toList();
    }

    private static Rule toRule(TenantRuleEntity e) {
        return switch (e.getRuleType()) {
            case "REQUIRED_FIELD" -> new RequiredField(e.getFieldName());
            case "AMOUNT_THRESHOLD" -> new AmountThreshold(
                    e.getMaxAmount(), Currency.getInstance(e.getCurrency()));
            case "APPROVAL_GATE" -> new ApprovalGate(e.getRequiredSignatures());
            default -> throw new IllegalStateException(
                    "unknown rule_type: " + e.getRuleType());
        };
    }
}
