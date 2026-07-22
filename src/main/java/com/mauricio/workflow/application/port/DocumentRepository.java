package com.mauricio.workflow.application.port;

import com.mauricio.workflow.domain.Document;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {

    Optional<Document> findById(UUID id);

    Document save(Document document);
}
