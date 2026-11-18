package com.example.rag.chat;

import java.util.List;
import java.util.UUID;

public record ChatAnswer(UUID conversationId, String answer, List<Source> sources) {

    public record Source(String documentId, String filename, Object chunkIndex, Double score, String excerpt) {
    }
}
