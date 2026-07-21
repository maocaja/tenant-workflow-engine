package com.mauricio.workflow.domain;

public sealed interface Rule
        permits RequiredField, AmountThreshold, ApprovalGate {
}
