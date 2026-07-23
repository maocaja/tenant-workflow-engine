package com.mauricio.workflow.infrastructure;

import com.mauricio.workflow.application.ApproveDocument;
import com.mauricio.workflow.application.CreateDraft;
import com.mauricio.workflow.application.SignDocument;
import com.mauricio.workflow.application.SubmitDocument;
import com.mauricio.workflow.domain.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentWorkflowService {

    private final CreateDraft createDraft;
    private final SubmitDocument submitDocument;
    private final SignDocument signDocument;
    private final ApproveDocument approveDocument;

    public DocumentWorkflowService(CreateDraft createDraft, SubmitDocument submitDocument,
                                   SignDocument signDocument, ApproveDocument approveDocument) {
        this.createDraft = createDraft;
        this.submitDocument = submitDocument;
        this.signDocument = signDocument;
        this.approveDocument = approveDocument;
    }

    @Transactional
    public Document create(UUID tenantId, String reference, BigDecimal amount,
                           Currency currency, Map<String, String> fields) {
        return createDraft.create(tenantId, reference, amount, currency, fields);
    }

    @Transactional
    public Document submit(UUID documentId, String actor) {
        return submitDocument.submit(documentId, actor);
    }

    @Transactional
    public Document sign(UUID documentId) {
        return signDocument.sign(documentId);
    }

    @Transactional
    public Document approve(UUID documentId, String actor) {
        return approveDocument.approve(documentId, actor);
    }
}
