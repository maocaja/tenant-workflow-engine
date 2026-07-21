package com.mauricio.workflow.domain;

public record RequiredField(
        String fieldName
) implements Rule {
    public RequiredField{
        if (fieldName == null || fieldName.isBlank()){
            throw new IllegalArgumentException(
                    "fieldName cannot be null or blank");
        }
    }
}
