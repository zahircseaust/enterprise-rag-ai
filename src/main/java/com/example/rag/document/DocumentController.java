package com.example.rag.document;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.example.rag.config.Tenants;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentRecord> upload(
            @RequestHeader(value = Tenants.HEADER, defaultValue = "default") String tenant,
            @RequestPart("file") MultipartFile file) throws IOException {
        DocumentRecord record = service.submit(Tenants.require(tenant), file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(record);
    }

    @GetMapping
    public List<DocumentRecord> list(@RequestHeader(value = Tenants.HEADER, defaultValue = "default") String tenant) {
        return service.list(Tenants.require(tenant));
    }

    @GetMapping("/{id}")
    public DocumentRecord get(@RequestHeader(value = Tenants.HEADER, defaultValue = "default") String tenant,
                              @PathVariable UUID id) {
        return service.get(Tenants.require(tenant), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(value = Tenants.HEADER, defaultValue = "default") String tenant,
                                       @PathVariable UUID id) {
        service.delete(Tenants.require(tenant), id);
        return ResponseEntity.noContent().build();
    }
}
