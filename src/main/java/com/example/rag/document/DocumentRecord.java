package com.example.rag.document;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentRecord(
        UUID id,
        String tenantId,
        String filename,
        String contentType,
        long sizeBytes,
        String sha256,
        DocumentStatus status,
        int chunkCount,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
