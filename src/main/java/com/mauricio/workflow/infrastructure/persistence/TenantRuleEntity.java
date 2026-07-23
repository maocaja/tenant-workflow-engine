package com.mauricio.workflow.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tenant_rules")
public class TenantRuleEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String gate;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "max_amount")
    private BigDecimal maxAmount;

    @Column
    private String currency;

    @Column(name = "required_signatures")
    private Integer requiredSignatures;

    protected TenantRuleEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getGate() {
        return gate;
    }

    public String getRuleType() {
        return ruleType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Integer getRequiredSignatures() {
        return requiredSignatures;
    }
}
