package com.mauricio.workflow.infrastructure.persistence;

import com.mauricio.workflow.domain.DocumentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String reference;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(nullable = false)
    private int signatures;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> fields;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY)
    @OrderBy("occurredAt asc")
    private List<AuditEventEntity> auditEvents = new ArrayList<>();

    protected DocumentEntity() {
    }

    public DocumentEntity(UUID id, UUID tenantId, String reference, BigDecimal amount,
                          String currency, DocumentStatus status, int signatures,
                          Map<String, String> fields, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.reference = reference;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.signatures = signatures;
        this.fields = fields;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getReference() {
        return reference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public int getSignatures() {
        return signatures;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<AuditEventEntity> getAuditEvents() {
        return auditEvents;
    }
}
