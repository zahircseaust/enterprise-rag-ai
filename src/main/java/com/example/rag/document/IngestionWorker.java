package com.example.rag.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Extract -> chunk -> enrich metadata -> embed + store. Runs off the request thread
 * (virtual threads are enabled) so large uploads don't block the HTTP call.
 */
@Component
public class IngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);
    private static final int WRITE_BATCH = 200;

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;
    private final DocumentRepository repository;

    public IngestionWorker(VectorStore vectorStore, TokenTextSplitter splitter, DocumentRepository repository) {
        this.vectorStore = vectorStore;
        this.splitter = splitter;
        this.repository = repository;
    }

    @Async
    public void ingest(DocumentRecord doc, byte[] content) {
        long start = System.currentTimeMillis();
        try {
            var resource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return doc.filename();
                }
            };

            List<Document> extracted = new TikaDocumentReader(resource).get();
            List<Document> chunks = splitter.apply(extracted);

            List<Document> enriched = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                if (chunk.getText() == null || chunk.getText().isBlank()) {
                    continue;
                }
                Map<String, Object> md = new HashMap<>(chunk.getMetadata());
                md.put("tenantId", doc.tenantId());
                md.put("documentId", doc.id().toString());
                md.put("filename", doc.filename());
                md.put("chunkIndex", i);
                enriched.add(Document.builder().text(chunk.getText()).metadata(md).build());
            }

            if (enriched.isEmpty()) {
                throw new IllegalStateException("No extractable text found in document");
            }

            for (int from = 0; from < enriched.size(); from += WRITE_BATCH) {
                vectorStore.add(enriched.subList(from, Math.min(from + WRITE_BATCH, enriched.size())));
            }

            repository.markReady(doc.id(), enriched.size());
            log.info("Ingested document {} ({}) tenant={} chunks={} in {} ms",
                    doc.id(), doc.filename(), doc.tenantId(), enriched.size(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Ingestion failed for document {} ({})", doc.id(), doc.filename(), e);
            repository.markFailed(doc.id(), truncate(e.getMessage()));
        }
    }

    private static String truncate(String s) {
        if (s == null) return "Unknown error";
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
