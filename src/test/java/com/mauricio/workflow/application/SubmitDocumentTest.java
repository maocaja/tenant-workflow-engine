package com.mauricio.workflow.application;

import com.mauricio.workflow.application.port.AuditRepository;
import com.mauricio.workflow.application.port.DocumentRepository;
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

class SubmitDocumentTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();

    private final FakeDocumentRepository documents = new FakeDocumentRepository();
    private final CapturingAuditRepository audit = new CapturingAuditRepository();
    private final StubTenantRules rules = new StubTenantRules();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final SubmitDocument submitDocument = new SubmitDocument(
            documents, rules, audit, new RuleEvaluator(), clock);

    private Document draft(int signatures, Map<String, String> fields) {
        return new Document(
                UUID.randomUUID(),
                TENANT,
                "FAC-001",
                new BigDecimal("100"),
                COP,
                DocumentStatus.DRAFT,
                signatures,
                fields,
                NOW
        );
    }

    @Nested
    @DisplayName("when the rules pass")
    class RulesPass {

        @Test
        @DisplayName("moves the document to SUBMITTED and persists it")
        void transitionsToSubmitted() {
            Document doc = draft(0, Map.of("diagnostico", "S72.0"));
            documents.store(doc);
            rules.set(new RequiredField("diagnostico"));

            Document result = submitDocument.submit(doc.id(), "ana");

            assertThat(result.status()).isEqualTo(DocumentStatus.SUBMITTED);
            assertThat(documents.findById(doc.id()))
                    .get()
                    .extracting(Document::status)
                    .isEqualTo(DocumentStatus.SUBMITTED);
        }

        @Test
        @DisplayName("writes a DRAFT to SUBMITTED audit event with no reason")
        void writesAuditWithoutReason() {
            Document doc = draft(0, Map.of());
            documents.store(doc);

            submitDocument.submit(doc.id(), "ana");

            AuditEvent event = audit.single();
            assertThat(event.documentId()).isEqualTo(doc.id());
            assertThat(event.from()).isEqualTo(DocumentStatus.DRAFT);
            assertThat(event.to()).isEqualTo(DocumentStatus.SUBMITTED);
            assertThat(event.actor()).isEqualTo("ana");
            assertThat(event.reason()).isNull();
            assertThat(event.occurredAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("when a rule fails")
    class RuleFails {

        @Test
        @DisplayName("moves the document to REJECTED and records the violation as the reason")
        void transitionsToRejected() {
            Document doc = draft(0, Map.of());
            documents.store(doc);
            rules.set(new RequiredField("diagnostico"));

            Document result = submitDocument.submit(doc.id(), "ana");

            assertThat(result.status()).isEqualTo(DocumentStatus.REJECTED);

            AuditEvent event = audit.single();
            assertThat(event.to()).isEqualTo(DocumentStatus.REJECTED);
            assertThat(event.reason()).contains("diagnostico");
        }

        @Test
        @DisplayName("joins every violation into a single reason")
        void joinsAllViolations() {
            Document doc = draft(0, Map.of());
            documents.store(doc);
            rules.set(
                    new RequiredField("diagnostico"),
                    new ApprovalGate(2));

            submitDocument.submit(doc.id(), "ana");

            assertThat(audit.single().reason())
                    .contains("diagnostico")
                    .contains("2");
        }
    }

    @Nested
    @DisplayName("guards")
    class Guards {

        @Test
        @DisplayName("rejects an unknown document id")
        void unknownDocument() {
            UUID missing = UUID.randomUUID();

            assertThatThrownBy(() -> submitDocument.submit(missing, "ana"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(missing.toString());
        }

        @Test
        @DisplayName("refuses to submit a document that is not a DRAFT")
        void notADraft() {
            Document doc = draft(0, Map.of()).withStatus(DocumentStatus.SUBMITTED);
            documents.store(doc);

            assertThatThrownBy(() -> submitDocument.submit(doc.id(), "ana"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SUBMITTED");
        }

        @Test
        @DisplayName("writes no audit event when the transition is refused")
        void noAuditOnRefusal() {
            Document doc = draft(0, Map.of()).withStatus(DocumentStatus.APPROVED);
            documents.store(doc);

            assertThatThrownBy(() -> submitDocument.submit(doc.id(), "ana"))
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
        public List<Rule> forTenant(UUID tenantId) {
            return rules;
        }
    }
}
