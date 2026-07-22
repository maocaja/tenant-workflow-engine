package com.mauricio.workflow.application;

import com.mauricio.workflow.application.port.AuditRepository;
import com.mauricio.workflow.application.port.DocumentRepository;
import com.mauricio.workflow.application.port.TenantRules;
import com.mauricio.workflow.domain.*;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class SubmitDocument {

    private final DocumentRepository documentRepository;
    private final TenantRules tenantRules;
    private final AuditRepository auditRepository;
    private final RuleEvaluator ruleEvaluator;
    private final Clock clock;

    public SubmitDocument(DocumentRepository documentRepository,
                          TenantRules tenantRules,
                          AuditRepository auditRepository,
                          RuleEvaluator ruleEvaluator,
                          Clock clock) {
        this.documentRepository = documentRepository;
        this.tenantRules = tenantRules;
        this.auditRepository = auditRepository;
        this.ruleEvaluator = ruleEvaluator;
        this.clock = clock;
    }

    public Document submit(UUID documentId, String actor) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "document not found: " + documentId
                ));
        if (document.status() != DocumentStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only DRAFT can be submitted, was " + document.status());
        }

        List<Rule> rules = tenantRules.forTenant(document.tenantId());

        // evalua todas, junta las que fallaron
        String violations = rules.stream()
                .map(rule -> ruleEvaluator.evaluate(rule, document))
                .flatMap(Optional::stream)
                .collect(Collectors.joining(", "));

        // decide el estado
        boolean rejected = !violations.isEmpty();

        DocumentStatus newStatus = rejected
                ? DocumentStatus.REJECTED
                : DocumentStatus.SUBMITTED;

        String reason = rejected ? violations : null;

        // audit antes de guardar el documento
        auditRepository.save(new AuditEvent(
                document.id(),
                DocumentStatus.DRAFT,
                newStatus,
                actor,
                reason,
                clock.instant()));

        return documentRepository.save(document.withStatus(newStatus));
    }
}
