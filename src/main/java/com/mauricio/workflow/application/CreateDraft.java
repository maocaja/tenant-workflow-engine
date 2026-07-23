package com.mauricio.workflow.application;

import com.mauricio.workflow.application.port.DocumentRepository;
import com.mauricio.workflow.domain.Document;
import com.mauricio.workflow.domain.DocumentStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;

public class CreateDraft {

    private final DocumentRepository documentRepository;
    private final Clock clock;

    public CreateDraft(DocumentRepository documentRepository, Clock clock) {
        this.documentRepository = documentRepository;
        this.clock = clock;
    }

    public Document create(UUID tenantId, String reference, BigDecimal amount,
                           Currency currency, Map<String, String> fields) {
        Document document = new Document(
                UUID.randomUUID(),
                tenantId,
                reference,
                amount,
                currency,
                DocumentStatus.DRAFT,
                0,
                fields,
                clock.instant());
        return documentRepository.save(document);
    }
}
