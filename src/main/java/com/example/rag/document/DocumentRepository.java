package com.example.rag.document;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private static final RowMapper<DocumentRecord> MAPPER = DocumentRepository::map;
    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public DocumentRecord insert(String tenantId, String filename, String contentType, long size, String sha256) {
        return jdbc.sql("""
                        INSERT INTO rag_document (id, tenant_id, filename, content_type, size_bytes, sha256, status)
                        VALUES (:id, :tenant, :filename, :contentType, :size, :sha, 'PROCESSING')
                        RETURNING *
                        """)
                .param("id", UUID.randomUUID())
                .param("tenant", tenantId)
                .param("filename", filename)
                .param("contentType", contentType)
                .param("size", size)
                .param("sha", sha256)
                .query(MAPPER)
                .single();
    }

    public Optional<DocumentRecord> findById(String tenantId, UUID id) {
        return jdbc.sql("SELECT * FROM rag_document WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId).param("id", id)
                .query(MAPPER).optional();
    }

    public Optional<DocumentRecord> findBySha(String tenantId, String sha256) {
        return jdbc.sql("SELECT * FROM rag_document WHERE tenant_id = :tenant AND sha256 = :sha")
                .param("tenant", tenantId).param("sha", sha256)
                .query(MAPPER).optional();
    }

    public List<DocumentRecord> findAll(String tenantId) {
        return jdbc.sql("SELECT * FROM rag_document WHERE tenant_id = :tenant ORDER BY created_at DESC")
                .param("tenant", tenantId)
                .query(MAPPER).list();
    }

    public void markReady(UUID id, int chunkCount) {
        jdbc.sql("""
                        UPDATE rag_document SET status = 'READY', chunk_count = :count,
                               error_message = NULL, updated_at = now() WHERE id = :id
                        """)
                .param("count", chunkCount).param("id", id).update();
    }

    public void markFailed(UUID id, String error) {
        jdbc.sql("""
                        UPDATE rag_document SET status = 'FAILED', error_message = :error,
                               updated_at = now() WHERE id = :id
                        """)
                .param("error", error).param("id", id).update();
    }

    public void delete(String tenantId, UUID id) {
        jdbc.sql("DELETE FROM rag_document WHERE tenant_id = :tenant AND id = :id")
                .param("tenant", tenantId).param("id", id).update();
    }

    private static DocumentRecord map(ResultSet rs, int row) throws SQLException {
        return new DocumentRecord(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getString("filename"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("sha256"),
                DocumentStatus.valueOf(rs.getString("status")),
                rs.getInt("chunk_count"),
                rs.getString("error_message"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
