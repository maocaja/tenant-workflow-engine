package com.mauricio.workflow.application;

import com.mauricio.workflow.application.port.AuditRepository;
import com.mauricio.workflow.application.port.DocumentRepository;
import com.mauricio.workflow.application.port.EventPublisher;
import com.mauricio.workflow.application.port.TenantRules;
import com.mauricio.workflow.domain.*;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ApproveDocument {

    private final DocumentRepository documentRepository;
    private final TenantRules tenantRules;
    private final AuditRepository auditRepository;
    private final EventPublisher eventPublisher;
    private final RuleEvaluator ruleEvaluator;
    private final Clock clock;

    public ApproveDocument(DocumentRepository documentRepository,
                           TenantRules tenantRules,
                           AuditRepository auditRepository,
                           EventPublisher eventPublisher,
                           RuleEvaluator ruleEvaluator,
                           Clock clock) {
        this.documentRepository = documentRepository;
        this.tenantRules = tenantRules;
        this.auditRepository = auditRepository;
        this.eventPublisher = eventPublisher;
        this.ruleEvaluator = ruleEvaluator;
        this.clock = clock;
    }

    public Document approve(UUID documentId, String actor) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "document not found: " + documentId
                ));
        if (document.status() != DocumentStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only SUBMITTED can be approved, was " + document.status());
        }

        List<Rule> rules = tenantRules.forTenant(document.tenantId(), Gate.APPROVE);

        String violations = rules.stream()
                .map(rule -> ruleEvaluator.evaluate(rule, document))
                .flatMap(Optional::stream)
                .collect(Collectors.joining(", "));

        boolean rejected = !violations.isEmpty();

        DocumentStatus newStatus = rejected
                ? DocumentStatus.REJECTED
                : DocumentStatus.APPROVED;

        String reason = rejected ? violations : null;

        auditRepository.save(new AuditEvent(
                document.id(),
                DocumentStatus.SUBMITTED,
                newStatus,
                actor,
                reason,
                clock.instant()));

        Document saved = documentRepository.save(document.withStatus(newStatus));

        if (!rejected) {
            eventPublisher.publish(new DocumentApproved(
                    document.id(),
                    document.tenantId(),
                    actor,
                    clock.instant()));
        }

        return saved;
    }
}
