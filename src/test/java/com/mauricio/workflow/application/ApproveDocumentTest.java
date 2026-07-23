package com.mauricio.workflow.application;

import com.mauricio.workflow.application.port.AuditRepository;
import com.mauricio.workflow.application.port.DocumentRepository;
import com.mauricio.workflow.application.port.EventPublisher;
import com.mauricio.workflow.application.port.TenantRules;
import com.mauricio.workflow.domain.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApproveDocumentTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();

    private final FakeDocumentRepository documents = new FakeDocumentRepository();
    private final CapturingAuditRepository audit = new CapturingAuditRepository();
    private final StubTenantRules rules = new StubTenantRules();
    private final CapturingEventPublisher events = new CapturingEventPublisher();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final ApproveDocument approveDocument = new ApproveDocument(
            documents, rules, audit, events, new RuleEvaluator(), clock);

    private Document submitted(int signatures) {
        return new Document(
                UUID.randomUUID(),
                TENANT,
                "FAC-001",
                new BigDecimal("100"),
                COP,
                DocumentStatus.SUBMITTED,
                signatures,
                Map.of(),
                NOW
        );
    }

    @Nested
    @DisplayName("when the approval rules pass")
    class RulesPass {

        @Test
        @DisplayName("moves the document to APPROVED and persists it")
        void transitionsToApproved() {
            Document doc = submitted(2);
            documents.store(doc);
            rules.set(new ApprovalGate(2));

            Document result = approveDocument.approve(doc.id(), "beto");

            assertThat(result.status()).isEqualTo(DocumentStatus.APPROVED);
            assertThat(documents.findById(doc.id()))
                    .get()
                    .extracting(Document::status)
                    .isEqualTo(DocumentStatus.APPROVED);
        }

        @Test
        @DisplayName("publishes a DocumentApproved event")
        void publishesApprovedEvent() {
            Document doc = submitted(2);
            documents.store(doc);
            rules.set(new ApprovalGate(2));

            approveDocument.approve(doc.id(), "beto");

            assertThat(events.published).hasSize(1);
            assertThat(events.published.get(0))
                    .isInstanceOf(DocumentApproved.class)
                    .extracting(e -> ((DocumentApproved) e).documentId())
                    .isEqualTo(doc.id());
        }

        @Test
        @DisplayName("writes a SUBMITTED to APPROVED audit event with no reason")
        void writesAuditWithoutReason() {
            Document doc = submitted(2);
            documents.store(doc);
            rules.set(new ApprovalGate(2));

            approveDocument.approve(doc.id(), "beto");

            AuditEvent event = audit.single();
            assertThat(event.documentId()).isEqualTo(doc.id());
            assertThat(event.from()).isEqualTo(DocumentStatus.SUBMITTED);
            assertThat(event.to()).isEqualTo(DocumentStatus.APPROVED);
            assertThat(event.actor()).isEqualTo("beto");
            assertThat(event.reason()).isNull();
            assertThat(event.occurredAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("when an approval rule fails")
    class RuleFails {

        @Test
        @DisplayName("moves the document to REJECTED and records the violation as the reason")
        void transitionsToRejected() {
            Document doc = submitted(0);
            documents.store(doc);
            rules.set(new ApprovalGate(2));

            Document result = approveDocument.approve(doc.id(), "beto");

            assertThat(result.status()).isEqualTo(DocumentStatus.REJECTED);

            AuditEvent event = audit.single();
            assertThat(event.from()).isEqualTo(DocumentStatus.SUBMITTED);
            assertThat(event.to()).isEqualTo(DocumentStatus.REJECTED);
            assertThat(event.reason()).contains("2").contains("0");
        }

        @Test
        @DisplayName("publishes no event when rejected")
        void publishesNothingOnRejection() {
            Document doc = submitted(0);
            documents.store(doc);
            rules.set(new ApprovalGate(2));

            approveDocument.approve(doc.id(), "beto");

            assertThat(events.published).isEmpty();
        }
    }

    @Nested
    @DisplayName("guards")
    class Guards {

        @Test
        @DisplayName("rejects an unknown document id")
        void unknownDocument() {
            UUID missing = UUID.randomUUID();

            assertThatThrownBy(() -> approveDocument.approve(missing, "beto"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(missing.toString());
        }

        @Test
        @DisplayName("refuses to approve a document that is not SUBMITTED")
        void notSubmitted() {
            Document doc = submitted(2).withStatus(DocumentStatus.DRAFT);
            documents.store(doc);

            assertThatThrownBy(() -> approveDocument.approve(doc.id(), "beto"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("writes no audit event when the transition is refused")
        void noAuditOnRefusal() {
            Document doc = submitted(2).withStatus(DocumentStatus.APPROVED);
            documents.store(doc);

            assertThatThrownBy(() -> approveDocument.approve(doc.id(), "beto"))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(audit.all()).isEmpty();
        }
    }

    // --- hand-rolled fakes -------------------------------------------------

    private static final class FakeDocumentRepository implements DocumentRepository {
        private final Map<UUID, Document> byId = new HashMap<>();

        void store(Document document) {
            byId.put(document.id(), document);
        }

        @Override
        public Optional<Document> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Document save(Document document) {
            byId.put(document.id(), document);
            return document;
        }
    }

    private static final class CapturingAuditRepository implements AuditRepository {
        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void save(AuditEvent event) {
            events.add(event);
        }

        List<AuditEvent> all() {
            return events;
        }

        AuditEvent single() {
            assertThat(events).hasSize(1);
            return events.get(0);
        }
    }

    private static final class StubTenantRules implements TenantRules {
        private List<Rule> rules = List.of();

        void set(Rule... rules) {
            this.rules = List.of(rules);
        }

        @Override
        public List<Rule> forTenant(UUID tenantId, Gate gate) {
            return rules;
        }
    }

    private static final class CapturingEventPublisher implements EventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }
}
