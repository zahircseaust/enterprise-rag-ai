-- nomic-embed-text produces 768-dim vectors; old 1536-dim rows are unusable, so drop them.
DROP INDEX IF EXISTS vector_store_embedding_hnsw_idx;
TRUNCATE vector_store;
ALTER TABLE vector_store ALTER COLUMN embedding TYPE vector(768);
CREATE INDEX vector_store_embedding_hnsw_idx ON vector_store USING hnsw (embedding vector_cosine_ops);
