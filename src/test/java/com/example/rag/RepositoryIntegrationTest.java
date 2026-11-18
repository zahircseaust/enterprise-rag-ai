package com.example.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.rag.chat.ConversationRepository;
import com.example.rag.document.DocumentRepository;
import com.example.rag.document.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Boots the full context against a real pgvector container; no LLM calls are made. */
@Testcontainers
@SpringBootTest
class RepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    DocumentRepository documents;

    @Autowired
    ConversationRepository conversations;

    @Test
    void documentLifecycleIsTenantScoped() {
        var doc = documents.insert("acme", "handbook.pdf", "application/pdf", 1234, "a".repeat(64));
        assertThat(doc.status()).isEqualTo(DocumentStatus.PROCESSING);

        documents.markReady(doc.id(), 42);
        assertThat(documents.findById("acme", doc.id())).get()
                .extracting(d -> d.chunkCount()).isEqualTo(42);
        assertThat(documents.findById("other-tenant", doc.id())).isEmpty();
    }

    @Test
    void conversationOwnership() {
        var id = conversations.create("acme");
        assertThat(conversations.findTenant(id)).contains("acme");
    }
}
