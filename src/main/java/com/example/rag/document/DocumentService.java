package com.example.rag.document;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.example.rag.web.NotFoundException;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final IngestionWorker worker;
    private final VectorStore vectorStore;

    public DocumentService(DocumentRepository repository, IngestionWorker worker, VectorStore vectorStore) {
        this.repository = repository;
        this.worker = worker;
        this.vectorStore = vectorStore;
    }

    /** Stores the catalog entry and starts async ingestion. Re-uploads of identical content are de-duplicated. */
    public DocumentRecord submit(String tenantId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        byte[] bytes = file.getBytes(); // read now: the multipart temp file is gone after the request
        String sha = sha256(bytes);

        var existing = repository.findBySha(tenantId, sha);
        if (existing.isPresent() && existing.get().status() != DocumentStatus.FAILED) {
            return existing.get();
        }
        existing.ifPresent(failed -> delete(tenantId, failed.id()));

        String filename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        DocumentRecord record = repository.insert(tenantId, filename, file.getContentType(), bytes.length, sha);
        worker.ingest(record, bytes);
        return record;
    }

    public List<DocumentRecord> list(String tenantId) {
        return repository.findAll(tenantId);
    }

    public DocumentRecord get(String tenantId, UUID id) {
        return repository.findById(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Document " + id + " not found"));
    }

    public void delete(String tenantId, UUID id) {
        DocumentRecord doc = get(tenantId, id);
        var b = new FilterExpressionBuilder();
        vectorStore.delete(b.and(
                b.eq("tenantId", tenantId),
                b.eq("documentId", doc.id().toString())).build());
        repository.delete(tenantId, id);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
