package com.mauricio.workflow.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

public class RuleEvaluator {

    public Optional<String> evaluate (Rule rule, Document document){
        return switch (rule) {
            case RequiredField(String fieldName) ->
                    document.fields().containsKey(fieldName)
                            ? Optional.empty()
                            : Optional.of("missing required field:" + fieldName);

            case AmountThreshold(BigDecimal max, Currency currency) -> {
                if (!document.currency().equals(currency)) {
                    yield Optional.of("currency mismatch: rule is " + currency
                            + ", document is " + document.currency());
                }
                yield document.amount().compareTo(max) <= 0
                        ? Optional.empty()
                        : Optional.of("amount " + document.amount()
                                + " exceeds limit " + max + " " + currency);
            }

            case ApprovalGate(int requiredSignatures) ->
                    document.signatures() >= requiredSignatures
                            ? Optional.empty()
                            : Optional.of("needs " + requiredSignatures
                                    + " signatures, has " + document.signatures());
        };
    }

}
