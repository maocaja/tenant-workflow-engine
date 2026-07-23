package com.mauricio.workflow.application;

import com.mauricio.workflow.application.port.DocumentRepository;
import com.mauricio.workflow.domain.Document;
import com.mauricio.workflow.domain.DocumentStatus;

import java.util.UUID;

public class SignDocument {

    private final DocumentRepository documentRepository;

    public SignDocument(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Document sign(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "document not found: " + documentId));
        if (document.status() != DocumentStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only SUBMITTED can be signed, was " + document.status());
        }
        return documentRepository.save(
                document.withSignatures(document.signatures() + 1));
    }
}
