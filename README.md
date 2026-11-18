# Enterprise RAG — Spring AI + PostgreSQL/pgvector

A production-shaped Retrieval-Augmented Generation service: upload documents, they are
parsed, chunked, embedded and stored in pgvector; questions are answered by an LLM that
is grounded in (and cites) the retrieved passages, with per-tenant isolation and
persistent conversation memory.

## Stack

| Layer            | Choice                                                          |
|------------------|-----------------------------------------------------------------|
| Runtime          | Java 21 (virtual threads), Spring Boot 3.5                      |
| AI framework     | Spring AI 1.1.x (`ChatClient`, advisors, modular RAG)           |
| LLM / embeddings | OpenAI (`gpt-4o-mini`, `text-embedding-3-small`, 1536 dims)     |
| Vector DB        | PostgreSQL 17 + pgvector, HNSW index, cosine distance           |
| Parsing          | Apache Tika (PDF, DOCX, PPTX, HTML, TXT, …)                     |
| Schema           | Flyway                                                          |
| Memory           | JDBC chat memory (sliding window) in the same Postgres          |
| Ops              | Actuator health probes, Prometheus metrics, Spring AI observations |

## Architecture

```
            ┌──────────── Ingestion (async) ────────────┐
 upload ──► │ Tika reader → TokenTextSplitter → metadata │──► EmbeddingModel ──► vector_store (pgvector)
            └─────────────────────────────────────────────┘          ▲
                                                                     │ similarity search
 question ─► ChatClient                                              │ filter: tenantId [&& documentId in …]
             ├─ MessageChatMemoryAdvisor  (history from Postgres)    │
             ├─ RetrievalAugmentationAdvisor ── (optional query rewrite) ── VectorStoreDocumentRetriever
             │     └─ ContextualQueryAugmenter (source-tagged context, refuses when empty)
             └─ ChatModel ──► answer + sources
```

## Project layout

```
src/main/java/com/example/rag
├── RagApplication.java
├── config/      AiConfig (ChatClient + RAG pipeline), RagProperties, Tenants
├── document/    upload, async ingestion, catalog repository, REST controller
├── chat/        RAG Q&A (blocking + SSE streaming), conversation ownership
├── security/    API-key filter
└── web/         RFC 7807 error handling
src/main/resources
├── application.yml
├── db/migration/V1__init.sql   vector_store, HNSW + GIN indexes, catalog tables
└── static/index.html           minimal UI (upload, list, streaming chat)
```

## Run it

Prerequisites: JDK 21, Maven 3.9+, Docker, an OpenAI API key.

```bash
docker compose up -d postgres            # pgvector on localhost:5432
export OPENAI_API_KEY=sk-...
mvn spring-boot:run
open http://localhost:8080               # bundled UI
```

Or run everything in containers:

```bash
cp .env.example .env   # fill in OPENAI_API_KEY
docker compose --profile app up --build
```

## API

All endpoints accept `X-Tenant-Id` (defaults to `default`) and, if `APP_API_KEY` is set, require `X-API-Key`.

```bash
# Upload (returns 202 + status PROCESSING; poll until READY)
curl -F file=@handbook.pdf -H 'X-Tenant-Id: acme' localhost:8080/api/v1/documents

# List / get / delete
curl -H 'X-Tenant-Id: acme' localhost:8080/api/v1/documents
curl -X DELETE -H 'X-Tenant-Id: acme' localhost:8080/api/v1/documents/{id}

# Ask (blocking, returns answer + sources with scores)
curl -H 'X-Tenant-Id: acme' -H 'Content-Type: application/json' \
     -d '{"question":"How many vacation days do new hires get?"}' \
     localhost:8080/api/v1/chat

# Follow-up in the same conversation, restricted to specific documents
curl -H 'X-Tenant-Id: acme' -H 'Content-Type: application/json' \
     -d '{"question":"And after five years?","conversationId":"<id>","documentIds":["<docId>"]}' \
     localhost:8080/api/v1/chat

# Streaming (SSE: meta → token… → done)
curl -N -H 'X-Tenant-Id: acme' -H 'Content-Type: application/json' \
     -d '{"question":"Summarize the travel policy"}' localhost:8080/api/v1/chat/stream

# Forget a conversation
curl -X DELETE -H 'X-Tenant-Id: acme' localhost:8080/api/v1/chat/conversations/{id}
```

Example blocking response:

```json
{
  "conversationId": "6f1c…",
  "answer": "New hires receive 20 days of paid leave per year [handbook.pdf#12].",
  "sources": [
    { "documentId": "9a…", "filename": "handbook.pdf", "chunkIndex": 12, "score": 0.81, "excerpt": "…" }
  ]
}
```

## Design decisions

- **Tenant isolation** — every chunk carries `tenantId` metadata and every search is filtered
  by it. Tenant ids are regex-validated before going into filter expressions (no injection).
  Conversations are owned by a tenant (`rag_conversation`), so memory can't be read across tenants.
- **Schema owned by Flyway** — `initialize-schema: false` for the vector store, so DDL is
  versioned and reviewed. HNSW index for ANN search, GIN index on metadata for fast filters.
- **Async, idempotent ingestion** — uploads return 202 immediately; identical content (SHA-256)
  per tenant isn't re-embedded; failures are recorded with an error message and can be retried
  by re-uploading.
- **Grounded answers** — the context is source-tagged and the prompt demands inline citations;
  when retrieval finds nothing, the model is told to say so instead of guessing.
- **Deletion is complete** — deleting a document removes its vectors by metadata filter.

## Tuning (`app.rag.*`)

| Property               | Default | Notes                                                   |
|------------------------|---------|---------------------------------------------------------|
| `chunk-size`           | 800     | tokens per chunk                                        |
| `top-k`                | 6       | passages per question                                   |
| `similarity-threshold` | 0.45    | raise for precision, lower for recall                   |
| `query-rewrite`        | false   | extra LLM call that rewrites vague questions for search |
| `memory-window`        | 20      | messages kept per conversation                          |

## Swapping providers

Run fully local with Ollama: replace `spring-ai-starter-model-openai` with
`spring-ai-starter-model-ollama`, configure `spring.ai.ollama.*` (e.g. chat `llama3.1`,
embedding `nomic-embed-text`) and change **both** `spring.ai.vectorstore.pgvector.dimensions`
and `vector(1536)` in a new Flyway migration to the embedding model's dimension (768 for
`nomic-embed-text`). Re-ingest documents after changing the embedding model.

## Spring AI version note

This project targets the Spring AI **1.1.x** line on Spring Boot **3.5**. Spring AI **2.0** is
GA and requires Spring Boot 4 / Spring Framework 7 with some breaking changes; follow the
official upgrade notes when moving to it. Keep patch versions current for security fixes.

## Production checklist

- Replace the API-key filter with Spring Security + OAuth2/OIDC and derive the tenant from the token.
- Move file bytes to object storage (S3/MinIO) and ingestion to a queue for horizontal scaling.
- Add rate limiting and token-budget tracking per tenant (Spring AI exposes token-usage metrics).
- Build an evaluation set (questions + expected sources) and run it in CI when tuning chunking/top-k.
- Consider hybrid search (pgvector + Postgres full-text) and a re-ranker for large corpora.

## Tests

```bash
mvn test   # unit tests + Testcontainers integration test (needs Docker)
```
