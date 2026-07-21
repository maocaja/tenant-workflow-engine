package com.mauricio.workflow.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluatorTest {

    private static final Currency COP = Currency.getInstance("COP");
    private static final Currency USD = Currency.getInstance("USD");

    private final RuleEvaluator evaluator = new RuleEvaluator();

    private Document document(BigDecimal amount, Currency currency,
                              int signatures, Map<String, String> fields) {
        return new Document(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "FAC-001",
                amount,
                currency,
                DocumentStatus.DRAFT,
                signatures,
                fields,
                Instant.parse("2026-07-22T10:00:00Z")
        );
    }

    @Nested
    @DisplayName("RequiredField")
    class RequiredFieldRule {

        @Test
        @DisplayName("passes when the document carries the field")
        void passesWhenPresent() {
            var doc = document(new BigDecimal("100"), COP, 0,
                    Map.of("diagnostico", "S72.0"));

            var result = evaluator.evaluate(new RequiredField("diagnostico"), doc);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("fails and names the missing field")
        void failsWhenMissing() {
            var doc = document(new BigDecimal("100"), COP, 0, Map.of());

            var result = evaluator.evaluate(new RequiredField("diagnostico"), doc);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("diagnostico");
        }
    }

    @Nested
    @DisplayName("AmountThreshold")
    class AmountThresholdRule {

        private final Rule limit5M =
                new AmountThreshold(new BigDecimal("5000000"), COP);

        @Test
        @DisplayName("passes when the amount is below the limit")
        void passesBelowLimit() {
            var doc = document(new BigDecimal("3500000"), COP, 0, Map.of());

            assertThat(evaluator.evaluate(limit5M, doc)).isEmpty();
        }

        @Test
        @DisplayName("passes when the amount equals the limit exactly")
        void passesAtLimit() {
            var doc = document(new BigDecimal("5000000"), COP, 0, Map.of());

            assertThat(evaluator.evaluate(limit5M, doc)).isEmpty();
        }

        @Test
        @DisplayName("fails when the amount exceeds the limit")
        void failsAboveLimit() {
            var doc = document(new BigDecimal("8000000"), COP, 0, Map.of());

            var result = evaluator.evaluate(limit5M, doc);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("8000000");
        }

        @Test
        @DisplayName("fails when the currency does not match, even if the amount fits")
        void failsOnCurrencyMismatch() {
            var doc = document(new BigDecimal("100"), USD, 0, Map.of());

            assertThat(evaluator.evaluate(limit5M, doc)).isPresent();
        }

        @Test
        @DisplayName("treats 5000000.00 and 5000000 as the same amount")
        void scaleDoesNotChangeTheAmount() {
            var doc = document(new BigDecimal("5000000.00"), COP, 0, Map.of());

            assertThat(evaluator.evaluate(limit5M, doc)).isEmpty();
        }
    }

    @Nested
    @DisplayName("ApprovalGate")
    class ApprovalGateRule {

        private final Rule needsTwo = new ApprovalGate(2);

        @Test
        @DisplayName("passes when the document has enough signatures")
        void passesWithEnough() {
            var doc = document(new BigDecimal("100"), COP, 2, Map.of());

            assertThat(evaluator.evaluate(needsTwo, doc)).isEmpty();
        }

        @Test
        @DisplayName("fails and reports required vs actual")
        void failsWithTooFew() {
            var doc = document(new BigDecimal("100"), COP, 0, Map.of());

            Optional<String> result = evaluator.evaluate(needsTwo, doc);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("2").contains("0");
        }
    }
}
