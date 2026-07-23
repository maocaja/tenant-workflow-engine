package com.mauricio.workflow.infrastructure.persistence;

import com.mauricio.workflow.application.port.DocumentRepository;
import com.mauricio.workflow.domain.Document;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaDocumentRepository implements DocumentRepository {

    private final DocumentJpaRepository jpa;

    public JpaDocumentRepository(DocumentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return jpa.findById(id).map(JpaDocumentRepository::toDomain);
    }

    @Override
    public Document save(Document document) {
        jpa.save(toEntity(document));
        return document;
    }

    static Document toDomain(DocumentEntity e) {
        return new Document(
                e.getId(),
                e.getTenantId(),
                e.getReference(),
                e.getAmount(),
                Currency.getInstance(e.getCurrency()),
                e.getStatus(),
                e.getSignatures(),
                e.getFields(),
                e.getCreatedAt());
    }

    private static DocumentEntity toEntity(Document d) {
        return new DocumentEntity(
                d.id(),
                d.tenantId(),
                d.reference(),
                d.amount(),
                d.currency().getCurrencyCode(),
                d.status(),
                d.signatures(),
                d.fields(),
                d.createdAt());
    }
}
