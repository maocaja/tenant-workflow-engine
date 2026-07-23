package com.mauricio.workflow.infrastructure.web;

import com.mauricio.workflow.infrastructure.persistence.DocumentJpaRepository;
import com.mauricio.workflow.infrastructure.web.dto.Responses.DocumentWithHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DocumentQueryService {

    private final DocumentJpaRepository documents;

    public DocumentQueryService(DocumentJpaRepository documents) {
        this.documents = documents;
    }

    public DocumentWithHistory byId(UUID id) {
        return documents.findById(id)
                .map(DocumentWithHistory::from)
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + id));
    }

    // Naive listing: each document's audit history is lazy-loaded on access,
    // so N documents trigger 1 + N queries. This is the N+1 surface (use case #3).
    public List<DocumentWithHistory> all() {
        return documents.findAll().stream()
                .map(DocumentWithHistory::from)
                .toList();
    }

    // Single-statement listing: the audit history is fetch-joined up front.
    public List<DocumentWithHistory> allFetched() {
        return documents.findAllWithAudit().stream()
                .map(DocumentWithHistory::from)
                .toList();
    }
}
