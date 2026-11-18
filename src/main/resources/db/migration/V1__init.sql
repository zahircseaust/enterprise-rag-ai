CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table used by Spring AI's PgVectorStore (default name: vector_store).
-- Dimension 1536 matches OpenAI text-embedding-3-small.
CREATE TABLE IF NOT EXISTS vector_store (
    id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(1536)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_hnsw_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);

-- Speeds up metadata filters (tenantId, documentId) used by Spring AI filter expressions.
CREATE INDEX IF NOT EXISTS vector_store_metadata_gin_idx
    ON vector_store USING gin ((metadata::jsonb) jsonb_path_ops);

-- Catalog of uploaded source documents.
CREATE TABLE IF NOT EXISTS rag_document (
    id            uuid PRIMARY KEY,
    tenant_id     varchar(64)  NOT NULL,
    filename      varchar(512) NOT NULL,
    content_type  varchar(255),
    size_bytes    bigint       NOT NULL,
    sha256        char(64)     NOT NULL,
    status        varchar(16)  NOT NULL,
    chunk_count   integer      NOT NULL DEFAULT 0,
    error_message text,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_rag_document_tenant_sha UNIQUE (tenant_id, sha256)
);

CREATE INDEX IF NOT EXISTS rag_document_tenant_idx ON rag_document (tenant_id, created_at DESC);

-- Ownership of chat conversations, so one tenant can never read another's memory.
CREATE TABLE IF NOT EXISTS rag_conversation (
    id         uuid PRIMARY KEY,
    tenant_id  varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
