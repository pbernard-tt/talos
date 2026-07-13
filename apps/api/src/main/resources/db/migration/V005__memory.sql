-- SPDX-FileCopyrightText: 2026 Vulkan Technologies
-- SPDX-License-Identifier: AGPL-3.0-or-later

-- Phase 13: project memory. pgvector is required because memory_chunks.embedding is queried with
-- vector distance; use a PostgreSQL 17 image that includes the extension (pgvector/pgvector:pg17).
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE memory_documents (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id),
  source_type VARCHAR(30) NOT NULL
    CHECK (source_type IN ('RUN_SUMMARY','OPERATOR_NOTE','CONTEXT_DOC')),
  source_ref VARCHAR(500) NOT NULL,
  title VARCHAR(300) NOT NULL,
  content TEXT NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (project_id, source_type, source_ref, content_hash)
);
CREATE INDEX idx_memory_documents_project ON memory_documents(project_id);

CREATE TABLE memory_chunks (
  id UUID PRIMARY KEY,
  document_id UUID NOT NULL REFERENCES memory_documents(id) ON DELETE CASCADE,
  project_id UUID NOT NULL REFERENCES projects(id),
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  embedding vector(64) NOT NULL,
  source_ref VARCHAR(500) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_memory_chunks_project ON memory_chunks(project_id);
