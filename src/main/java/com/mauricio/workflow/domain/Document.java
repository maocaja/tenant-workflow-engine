package com.mauricio.workflow.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;

public record Document(
        UUID id,
        UUID tenantId,
        String reference,
        BigDecimal amount,
        Currency currency,
        DocumentStatus status,
        int signatures,
        Map<String, String> fields,
        Instant createdAt
) {
    public Document {

        if(id == null)
            throw new IllegalArgumentException(
                    "id is required"
            );

        if(tenantId == null)
            throw new IllegalArgumentException(
                    "tenant is required"
            );

        if(status == null)
            throw new IllegalArgumentException(
                    "status is required"
            );

        if(createdAt == null)
            throw new IllegalArgumentException(
                    "createdAt is required"
            );

        if(amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException(
                    "amount must be positive"
            );

        if(currency == null )
            throw new IllegalArgumentException(
                    "currency is required"
            );


        if (fields == null) throw new IllegalArgumentException("fields is required");

        if(reference == null || reference.isBlank())
            throw new IllegalArgumentException(
                    "reference cannot be null or blank"
            );

        if (signatures < 0)
            throw new IllegalArgumentException(
                    "signatures cannot be negative"
            );

        fields = Map.copyOf(fields);
    }

    public Document withStatus(DocumentStatus newStatus) {
        return new Document(id, tenantId, reference, amount, currency,
                newStatus, signatures, fields, createdAt);
    }

    public Document withSignatures(int newSignatures) {
        return new Document(id, tenantId, reference, amount, currency,
                status, newSignatures, fields, createdAt);
    }
}