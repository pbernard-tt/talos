-- Phase 16: MinIO artifact storage (Section 4.2/10.4). Durable metadata for artifacts written
-- through the ArtifactStore interface -- the file bytes themselves live in whichever
-- ArtifactStore implementation is configured (LocalVolumeArtifactStore or MinioArtifactStore),
-- keyed by storage_key; this table is what makes an artifact listable/downloadable/deletable
-- without talking to the store first.
CREATE TABLE run_artifacts (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES agent_runs(id),
    kind VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_run_artifacts_run_id ON run_artifacts (run_id);
