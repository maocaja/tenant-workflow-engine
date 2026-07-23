package com.mauricio.workflow.application.port;

import com.mauricio.workflow.domain.DomainEvent;

public interface EventPublisher {

    void publish(DomainEvent event);
}
