package com.mauricio.workflow.application.port;

import com.mauricio.workflow.domain.AuditEvent;

public interface AuditRepository {

    void save(AuditEvent event);
}
