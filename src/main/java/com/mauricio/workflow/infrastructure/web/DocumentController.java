package com.mauricio.workflow.infrastructure.web;

import com.mauricio.workflow.domain.Document;
import com.mauricio.workflow.infrastructure.DocumentWorkflowService;
import com.mauricio.workflow.infrastructure.web.dto.Requests.ActorRequest;
import com.mauricio.workflow.infrastructure.web.dto.Requests.CreateDraftRequest;
import com.mauricio.workflow.infrastructure.web.dto.Responses.DocumentResponse;
import com.mauricio.workflow.infrastructure.web.dto.Responses.DocumentWithHistory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentWorkflowService workflow;
    private final DocumentQueryService queries;

    public DocumentController(DocumentWorkflowService workflow, DocumentQueryService queries) {
        this.workflow = workflow;
        this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse create(@Valid @RequestBody CreateDraftRequest req) {
        Document d = workflow.create(
                req.tenantId(),
                req.reference(),
                req.amount(),
                Currency.getInstance(req.currency()),
                req.fields() == null ? Map.of() : req.fields());
        return DocumentResponse.from(d);
    }

    @PostMapping("/{id}/submit")
    public DocumentResponse submit(@PathVariable UUID id, @Valid @RequestBody ActorRequest req) {
        return DocumentResponse.from(workflow.submit(id, req.actor()));
    }

    @PostMapping("/{id}/signatures")
    public DocumentResponse sign(@PathVariable UUID id) {
        return DocumentResponse.from(workflow.sign(id));
    }

    @PostMapping("/{id}/approve")
    public DocumentResponse approve(@PathVariable UUID id, @Valid @RequestBody ActorRequest req) {
        return DocumentResponse.from(workflow.approve(id, req.actor()));
    }

    @GetMapping("/{id}")
    public DocumentWithHistory byId(@PathVariable UUID id) {
        return queries.byId(id);
    }

    @GetMapping
    public List<DocumentWithHistory> all() {
        return queries.all();
    }
}
