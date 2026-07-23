package com.mauricio.workflow.infrastructure;

import com.mauricio.workflow.infrastructure.web.DocumentQueryService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocumentListingN1Test {

    private static final UUID CLIENT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Currency COP = Currency.getInstance("COP");

    @Autowired
    private DocumentWorkflowService workflow;

    @Autowired
    private DocumentQueryService queries;

    @Autowired
    private EntityManagerFactory emf;

    private Statistics statistics() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    private int seedSubmittedDocuments(int n) {
        for (int i = 0; i < n; i++) {
            var doc = workflow.create(CLIENT_B, "FAC-" + i,
                    new BigDecimal("1000000"), COP, Map.of());
            workflow.submit(doc.id(), "seed");
        }
        return n;
    }

    @Test
    @DisplayName("naive listing fires 1 + N statements — the N+1")
    void naiveListingIsNPlusOne() {
        int n = seedSubmittedDocuments(5);

        Statistics stats = statistics();
        stats.clear();

        var result = queries.all();

        long statements = stats.getPrepareStatementCount();
        System.out.println(">>> naive listing of " + result.size()
                + " documents used " + statements + " statements");
        assertThat(result).hasSizeGreaterThanOrEqualTo(n);
        assertThat(statements)
                .as("1 query for the documents + 1 per document for its audit collection")
                .isGreaterThanOrEqualTo(1 + n);
    }

    @Test
    @DisplayName("fetch-joined listing collapses to a single statement")
    void fetchJoinedIsOneStatement() {
        seedSubmittedDocuments(5);

        Statistics stats = statistics();
        stats.clear();

        var result = queries.allFetched();

        long statements = stats.getPrepareStatementCount();
        System.out.println(">>> fetch-joined listing of " + result.size()
                + " documents used " + statements + " statements");
        assertThat(statements)
                .as("a single left-join fetch loads documents and their audit events")
                .isEqualTo(1);
    }
}
