package com.mauricio.workflow.domain;

public record ApprovalGate (
        int requiredSignatures
) implements Rule {

    public ApprovalGate {
        if (requiredSignatures <= 0){
            throw new IllegalArgumentException(
                    "requiredSignatures must be at least 1"
            );
        }
    }
}
