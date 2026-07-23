package com.mauricio.workflow.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public final class Requests {

    private Requests() {
    }

    public record CreateDraftRequest(
            @NotNull UUID tenantId,
            @NotNull String reference,
            @NotNull @Positive BigDecimal amount,
            @NotNull String currency,
            Map<String, String> fields) {
    }

    public record ActorRequest(
            @NotNull String actor) {
    }
}
