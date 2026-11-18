package com.example.rag.chat;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param question        the user's question
 * @param conversationId  optional; omit to start a new conversation
 * @param documentIds     optional; restrict retrieval to these documents
 */
public record ChatRequest(
        @NotBlank @Size(max = 4000) String question,
        UUID conversationId,
        @Size(max = 50) List<UUID> documentIds) {
}
