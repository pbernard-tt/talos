# Phase 13 report — Memory

Phase 13 is complete. Project-scoped memory now exists end to end: API-owned ingestion,
API-owned embedding/retrieval, pgvector-backed storage, orchestrator prompt injection, and the
`talos.yaml` disable switch.

## What works

- **Schema and infrastructure.** `V005__memory.sql` enables `vector`, creates
  `memory_documents`, and creates `memory_chunks` with a `vector(64)` embedding column. The local
  and Dokploy Postgres services now use `pgvector/pgvector:pg17`; API Testcontainers were updated
  to the same compatible image so migrations run against the real extension.
- **API-owned ingestion and retrieval.** `dev.talos.memory` owns chunking, masking, embedding,
  deduplication, and vector search. The Python services still never open a database connection.
  Public operator ingestion is available at `POST /api/v1/projects/{id}/memory/documents`.
  Internal orchestrator ingestion is available at `POST /internal/v1/projects/{id}/memory/documents`.
  Retrieval is internal-only at `GET /internal/v1/projects/{id}/memory/search`.
- **Memory sources.** Completed runs are ingested as `RUN_SUMMARY` documents when the run reaches
  `COMPLETED`, including task title/description, run summary, changed-file digest, and a bounded
  diff digest. Operator notes and context documents use the same API path with `OPERATOR_NOTE` or
  `CONTEXT_DOC`.
- **Context docs ingestion.** For real coding adapters, the orchestrator reads configured
  `talos.yaml context.docs` files from the prepared isolated worktree and sends their contents to
  the internal memory endpoint before searching memory. API-side uniqueness on
  `(project_id, source_type, source_ref, content_hash)` prevents repeated runs from duplicating
  identical chunks.
- **Prompt assembly.** Memory search results are inserted into the Section 7.3 prompt as
  `Relevant project memory` after direct project context and before the task. The assembled prompt
  is still persisted to `agent_runs.prompt`.
- **Disable switch.** `talos.yaml` now accepts:

  ```yaml
  memory:
    enabled: false
    prompt_budget_chars: 4000
  ```

  With `enabled: false`, the orchestrator skips context-doc ingestion and memory search, and the
  prompt regression test asserts byte-identical output to the pre-Phase-13 prompt. `CustomShellAdapter`
  retains its Phase 6 command semantics.
- **Project isolation and masking.** Search always filters by `project_id`; tests prove a same query
  does not read chunks from another project. Memory text is passed through the secret masking sweep
  before persistence.
- **Contracts and generated client.** `packages/contracts/openapi.yaml` is at `0.13.0` and includes
  public memory ingestion plus the internal ingestion/search endpoints. The Angular OpenAPI client
  was regenerated after tagging internal memory endpoints as `internal` only, avoiding duplicate
  generated exports.

## Documented deviations

1. **Embedding provider is deterministic/local for Phase 13.** Revision 2.1 says embeddings are
   computed through a configured BYOK provider, but it does not name a provider protocol, model, or
   request/response contract. To avoid inventing a subscription dependency or leaking provider
   credentials into an unfinished contract, this phase implements an `EmbeddingProvider` boundary
   with a deterministic lexical `HashEmbeddingProvider`. It keeps ingestion/retrieval functional,
   testable, self-hosted, and replaceable once the concrete BYOK provider contract is chosen.
2. **Prompt budget is character-based.** The plan says "token budget"; no tokenizer/model binding is
   defined yet. The implemented `memory.prompt_budget_chars` cap is explicit about using characters
   and is enforced server-side during retrieval. A future external embedding/model provider can add
   true token budgeting without changing the memory table ownership model.
3. **No dashboard memory management UI yet.** Operator notes can be ingested through the public API
   and are in the OpenAPI client, but no Angular screen was added for browsing or editing project
   memory. Phase 13's acceptance criteria are covered by API/orchestrator behavior, not by a new UI.

## Stubbed / deferred

- External BYOK embedding provider implementation and operator configuration.
- Memory browse/delete UI.
- Advanced vector indexes (`ivfflat`/`hnsw`) and tuning. The current sequential vector search is
  sufficient for the MVP-scale memory corpus and easier to validate deterministically.

## Verification

- `apps/api`: targeted Phase 13 slice:
  `sg docker -c 'cd /home/paulb/Personal/Talos/apps/api && GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon test --tests "dev.talos.memory.*" --tests "dev.talos.projects.TalosConfigParserTest" --tests "dev.talos.runs.RunServiceRetentionCandidatesTest"'`
  — BUILD SUCCESSFUL.
- `apps/api`: full suite:
  `sg docker -c 'cd /home/paulb/Personal/Talos/apps/api && GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon test'`
  — BUILD SUCCESSFUL.
- `apps/orchestrator`: `UV_CACHE_DIR=/tmp/uv-cache uv run pytest` — 30 passed.
- `packages/contracts/openapi.yaml`: parsed with Python/YAML; version `0.13.0`; internal memory
  document path present.
- `apps/web`: `npm run generate:api` succeeded. Angular build succeeded under Node 22.23.1 via the
  direct Node 22 binary after the default shell's Homebrew Node 24.11.1 failed the Angular version
  gate and sandboxed DNS blocked Google Fonts; the final approved-network build completed and
  emitted `dist/talos-web`.
- PDF sync: `bash docs/src/build-pdf.sh` succeeded with network approval after sandboxed DNS failed
  to fetch the npm markdown renderer; `md5sum` confirmed `docs/src/talos-implementation-plan.pdf`
  matches `docs/Talos_Implementation_Plan.pdf`.
- Compose rendering: dev compose passed with `TALOS_DOCKER_GID=999`; Dokploy production compose
  passed with sample required environment variables.
- `git diff --check` passed after stripping trailing whitespace emitted by the generated Angular
  client.
- Source-scoped naming guard returned no matches.
