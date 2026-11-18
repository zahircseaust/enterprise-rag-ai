package com.example.rag.chat;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepository {

    private final JdbcClient jdbc;

    public ConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID create(String tenantId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO rag_conversation (id, tenant_id) VALUES (:id, :tenant)")
                .param("id", id).param("tenant", tenantId).update();
        return id;
    }

    public Optional<String> findTenant(UUID id) {
        return jdbc.sql("SELECT tenant_id FROM rag_conversation WHERE id = :id")
                .param("id", id).query(String.class).optional();
    }

    public void delete(UUID id) {
        jdbc.sql("DELETE FROM rag_conversation WHERE id = :id").param("id", id).update();
    }
}
