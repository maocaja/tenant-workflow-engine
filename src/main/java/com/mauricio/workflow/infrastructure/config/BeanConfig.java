package com.mauricio.workflow.infrastructure.config;

import com.mauricio.workflow.application.*;
import com.mauricio.workflow.application.port.AuditRepository;
import com.mauricio.workflow.application.port.DocumentRepository;
import com.mauricio.workflow.application.port.EventPublisher;
import com.mauricio.workflow.application.port.TenantRules;
import com.mauricio.workflow.domain.RuleEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BeanConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RuleEvaluator ruleEvaluator() {
        return new RuleEvaluator();
    }

    @Bean
    CreateDraft createDraft(DocumentRepository documents, Clock clock) {
        return new CreateDraft(documents, clock);
    }

    @Bean
    SubmitDocument submitDocument(DocumentRepository documents, TenantRules rules,
                                  AuditRepository audit, RuleEvaluator evaluator, Clock clock) {
        return new SubmitDocument(documents, rules, audit, evaluator, clock);
    }

    @Bean
    ApproveDocument approveDocument(DocumentRepository documents, TenantRules rules,
                                    AuditRepository audit, EventPublisher events,
                                    RuleEvaluator evaluator, Clock clock) {
        return new ApproveDocument(documents, rules, audit, events, evaluator, clock);
    }

    @Bean
    SignDocument signDocument(DocumentRepository documents) {
        return new SignDocument(documents);
    }
}
