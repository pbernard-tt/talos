## 2026-07-11 — Review gap #1: GitHub webhook + PR status transitions (unblocks retention)

**Ask:** Begin working through `docs/initial_review.md`; item #1 (critical): implement the
plan-mandated `POST /api/v1/webhooks/github` (Sections 6.2, 10.1, 10.2) — without it
`pull_requests.status` never leaves `OPEN`, so the Phase 11 retention sweep skips every run that
produced a PR and its workspace is kept forever.

**Changed (backend, `apps/api`):**
- `dev.talos.webhooks` (new package, per Section 6.2) — `GithubWebhookController`
  (`POST /api/v1/webhooks/github`, raw-byte body so the HMAC covers the exact wire payload, 204 on
  success) and `GithubWebhookService` (HMAC-SHA256 verification of `X-Hub-Signature-256` against
  `talos.github-webhook-secret`, constant-time compare, fail-closed when the secret is
  unconfigured; `pull_request` `closed` → `MERGED`/`CLOSED` by the payload's `merged` flag,
  `reopened` → `OPEN`; rows matched by PR html URL because numbers collide across repos; unknown
  PRs and other event types acknowledged with 204 — GitHub webhooks are repo-wide, so those are
  expected; each transition audited as `pr.status.changed`).
- `SecurityConfig` — `permitAll` for `/api/v1/webhooks/**` in the JWT chain (Section 10.1: webhook
  base path authenticates by signature, not JWT).
- `PullRequest.setStatus` + `PullRequestRepository.findByProviderAndUrl` — first mutation path for
  PR status, previously immutable after creation (the root of the retention bug).

**Changed (contracts):** `openapi.yaml` — `/webhooks/github` path (security: [], header params,
204/401), version 0.16.0 → 0.17.0.

**Verification:** new `GithubWebhookControllerIntegrationTest` (5 tests: merged → `MERGED` +
audit row; closed-unmerged → `CLOSED` then reopened → `OPEN`; bad signature → 401
`WEBHOOK_SIGNATURE_INVALID` with status unchanged; missing signature → 401; unknown PR and `ping`
events → 204 no-op). Full `apps/api` suite green (BUILD SUCCESSFUL, 4m50s, 180 tests). Naming
guard clean. Not checked: a live GitHub delivery (needs a reachable deployment); the payload
shapes used match GitHub's documented `pull_request` event schema.

**Known blockers/follow-ups:** webhook registration on the repo is operator-side (GitHub repo
settings → webhook URL + `TALOS_GITHUB_WEBHOOK_SECRET`); no polling fallback for deliveries missed
while Talos is down — acceptable because a later `closed`/`reopened` delivery or manual DB fix can
recover, but worth a runbook note.

## 2026-07-11 — Fix CI: runner-supervisor push commits had no committer identity

**Ask:** CI's runner-supervisor job failing — 4 tests (`test_git_push.py` ×3, plus
`test_app.py::test_push_endpoint_commitsAndPushes` returning 422) with git's
"Committer identity unknown".

**Root cause:** `git_push.push()` committed with `--author=` only; `--author` does not set the
*committer*, which git resolves from ambient config. Dev machines have a global identity, CI
runners don't — and the supervisor's Docker image doesn't set one either, so every real
approved-push would have failed identically in production. The 422 was the same error surfacing
through the endpoint's `GitPushError → 422` mapping. One test also made its own "agent" commit in
the worktree relying on ambient identity.

**Changed (backend, `apps/runner-supervisor`):**
- `git_push.py` — commit now passes `-c user.name=Talos Agent -c user.email=talos@local` on the
  invocation itself, so the runner never depends on host/container git config (production fix).
- `tests/test_git_push.py` — the simulated agent commit carries its own `-c` identity flags
  (a real agent configures its own identity; the supervisor shouldn't provide one for it).

**Verification:** full suite (31 tests) green under
`GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null` with all `GIT_*`/`EMAIL` identity env
vars unset — i.e. reproducing CI's no-identity environment, which also reproduced the failure
before the fix. Not checked: an actual CI run — happens on push.

## 2026-07-11 — Fix CI: two API integration tests depended on the dev-compose Redis

**Ask:** GitHub CI's `api` job was failing — five tests across `MemoryControllerIntegrationTest`
and `TaskControllerIntegrationTest` with `RedisConnectionFailureException`.

**Root cause:** both tests reached Redis at `localhost:6379` — the Task test set
`spring.data.redis.url` there explicitly, the Memory test fell through to Spring's default. That
only works on a dev machine with the compose Redis running; the CI runner has no Redis, so the
first login in each test (rate limiting touches Redis) failed. Every other integration test
already runs its own Redis testcontainer.

**Changed (backend, `apps/api` tests only):**
- `TaskControllerIntegrationTest.java` — replaced the hardcoded `redis://localhost:6379` with a
  `redis:7` `GenericContainer` and its mapped port, matching `AuthControllerIntegrationTest`.
- `MemoryControllerIntegrationTest.java` — added the same Redis container plus a
  `@DynamicPropertySource` (it previously had no Redis wiring at all).

**Verification:** both test classes green individually, then the full `apps/api` suite green
(`./gradlew test`, 4m47s, Testcontainers via `sg docker`). The local pass is meaningful for CI
because the tests now target the container's mapped port, not the dev Redis. Not checked: an
actual CI run — happens on push.

## 2026-07-11 — Post-implementation gap review (docs/initial_review.md)

**Ask:** With all planned phases (0–16) implemented, review the implementation plan against the
codebase to identify gaps left behind or issues to address before production; log the findings in
`docs/initial_review.md`.

**Changed (docs):**
- `docs/initial_review.md` (new) — full findings. Headlines: `POST /api/v1/webhooks/github` never
  implemented, which also leaves `pull_requests.status` stuck `OPEN` and permanently blocks the
  retention sweep for any run with a PR; no UI path to start a run; latest 9 commits never pushed,
  so CI hasn't exercised Phases ~13–16; approval expiry/reminders inert; runner supervisor
  endpoints still unauthenticated (open Phase 11 deviation); Command Center/Integrations/approvals
  UI screens missing; `talos-web` absent from the dev compose; plus medium/low items (no OpenAPI
  drift check, `TALOS_MAX_ACTIVE_RUNS` unenforced, unconsumed DLQ, missing adapter `.env.example`s).

**Verification:** All suites re-run from scratch for the review, not taken from phase reports:
`apps/api` 175 tests green (`--rerun-tasks`, Testcontainers), `packages/agent-adapter-spec` 75
green (with Docker; its container tests error rather than skip when the docker binary exists but
the socket is inaccessible — noted in the review), orchestrator 33, runner-supervisor 31,
telegram-adapter 31, whatsapp-adapter 39, `apps/web` 14 green under Node 22.23.1, scoped naming
guard clean. Endpoint/schema/event/adapter/UI-route claims verified by direct grep/read of the
code, not report text. Not checked: a live compose-up smoke run (CI's smoke job covers it, but has
not run on the unpushed commits — that is itself finding #3).

## 2026-07-11 — Phase 16: MinIO artifact storage

**Ask:** Implement Phase 16 per Section 16 of the plan: move run artifacts (transcripts, patches,
test reports, generated docs) behind an `ArtifactStore` interface with `LocalVolumeArtifactStore`
(default) and `MinioArtifactStore` implementations, finally building the
`POST /internal/v1/runs/{id}/artifacts` endpoint Section 10.4 had reserved but no phase had
implemented; extend Phase 11's retention sweep to the object store; add a one-shot migration path.

**Changed (backend, `apps/api`):**
- `V008__run_artifacts.sql` — new `run_artifacts` table (id, run_id, kind, name, storage_key,
  content_type, size_bytes, created_at).
- `dev.talos.artifacts` (new package) — `ArtifactStore` interface; `LocalVolumeArtifactStore`
  (path-traversal-guarded); `MinioArtifactStore` (`io.minio:minio`, ensures its bucket on
  construction, never logs credentials); `ArtifactStoreConfig` (`@ConditionalOnProperty` bean
  selection on `talos.artifact-store-type`); `ArtifactService` (name validation, storage-key
  layout, the only caller of `ArtifactStore`); `ArtifactMigrationRunner` (boot-time one-shot
  local -> MinIO copy, gated by `talos.migrate-artifacts-on-boot`).
- `InternalRunController` — `POST`/`DELETE /internal/v1/runs/{id}/artifacts` (multipart upload;
  delete-all for retention).
- `RunController` — `GET /api/v1/runs/{id}/artifacts` (list), `GET .../artifacts/{id}/download`
  (streams bytes, `Content-Disposition: attachment`).
- `TalosProperties`/`application.yml` — `artifact-store-type`, `artifact-local-dir`,
  `minio-endpoint/access-key/secret-key/bucket`, `migrate-artifacts-on-boot`; multipart size limits
  raised to 64MB.
- `apps/api/Dockerfile` — pre-creates and chowns `/var/talos/artifacts` to the non-root `talos` user
  (found live, see below).

**Changed (`apps/runner-supervisor`, `apps/orchestrator`):**
- `artifact_client.py` (new) — posts artifacts directly to `talos-api`'s `/internal/v1` (not
  relayed through the orchestrator; see deviation below), best-effort (logged, never raised).
- `diff_capture` flow (`app.py`) posts `diff.patch` after every diff capture; `execute.py` posts the
  agent transcript (`AgentResult.raw_output_path`, previously computed but never forwarded past this
  service); `test_command.py` now tees test output to `test-report.log` alongside streaming it, and
  posts that file too.
- `apps/orchestrator/retention.py`/`api_client.py` — `delete_artifacts(run_id)`, called once per run
  id the runner supervisor's cleanup actually reported deleted (not every candidate).

**Changed (contracts/frontend):**
- `packages/contracts/openapi.yaml` — version `0.16.0`; `ArtifactKind`/`RunArtifact` schemas, four
  new paths.
- `apps/web` — regenerated Angular client; run detail page gained an Artifacts section with a
  download button (fetches the authenticated blob, then triggers a browser download — not a raw
  `<a href>`, since the endpoint needs the JWT bearer header).
- `infra/docker-compose.dev.yml` — new `talos-minio` service + `talos_artifacts` volume;
  `talos-api`'s `TALOS_ARTIFACT_STORE_TYPE` left unset by default (unconfigured behavior unchanged).

**Found live (not by unit tests):** the first `scripts/smoke.sh` run against the rebuilt dev stack
reached `WAITING_APPROVAL` but every artifact post 502'd — `docker exec talos-api id` showed the
container runs as non-root `talos` (uid 999) and `/var/talos` didn't exist at all; nothing before
this phase ever needed a writable path outside the JVM's own working directory. Fixed in the
Dockerfile (`mkdir -p /var/talos/artifacts && chown -R talos:talos /var/talos` before `USER talos`)
plus a named volume mount so ownership persists across recreates. Re-verified: `scripts/smoke.sh`
passed and both `diff.patch`/`transcript.txt` showed up via `GET /api/v1/runs/{id}/artifacts`.

**Documented deviations:** the runner supervisor posts artifacts to `talos-api` directly rather than
relayed through the orchestrator — Section 10.1 already lists the runner supervisor as an allowed
`/internal/v1` caller and Appendix A's token row for it was already annotated "artifact/log posts"
since Phase 6, so this uses an already-reserved path rather than inventing a new
runner-supervisor -> orchestrator relay; `GENERATED_DOC` has no producer yet (schema/storage support
exists, nothing emits one); migration is a boot-time flag (mirroring
`IntegrationServiceAccountSeeder`'s shape), not a standalone CLI tool. Full detail in
`docs/phase-reports/phase-16-report.md`.

## 2026-07-10 — Phase 15: Multi-user RBAC enforcement

**Ask:** Implement Phase 15 per Section 16 of the plan: replace MVP owner-mode with real
enforcement of the OWNER/MAINTAINER/REVIEWER/VIEWER roles that have existed in the schema since
Phase 1 — user management, a server-side authorization matrix, self-approval prohibition with an
audited OWNER override, and role-aware (but never role-*enforced*) UI hiding.

**Changed (backend, `apps/api`):**
- `V007__rbac.sql` — `users.active` (default true) and `agent_runs.requested_by`.
- `SecurityConfig` — `@EnableMethodSecurity` plus a `RoleHierarchy` bean (`OWNER > MAINTAINER >
  REVIEWER > VIEWER`) so `hasRole('MAINTAINER')` also admits OWNER.
- `TaskController`/`ProjectController`/`RunController`/`ApprovalController`/`IntegrationController`/
  `MemoryController` — `@PreAuthorize` added per the plan's tier breakdown (VIEWER read-only;
  REVIEWER adds approve/reject/request-changes; MAINTAINER adds project/task CRUD, start-run,
  cancel, memory ingestion; OWNER adds integrations, deploy trigger).
- `dev.talos.auth.AuthorizationExceptionHandler` (new `@RestControllerAdvice`) — the actual
  `@PreAuthorize` denial path: 403 plus a `role.access_denied` audit row. Kept out of
  `dev.talos.common.GlobalExceptionHandler` to avoid a reverse package dependency.
- `dev.talos.auth.UserController`/`UserService` (new, OWNER-only) — create/list/update (role,
  active) users; an OWNER cannot modify their own role/active status through this endpoint.
- `User` — gained `active`; `AuthService.login` folds the active check into the existing
  password-match filter (same generic `INVALID_CREDENTIALS` response either way).
- `AgentRun` — gained `requestedBy` (set from `start-run`'s caller); `RunService.createApproval`
  now passes it into the auto-created `RUN_RESULT` approval instead of `null`.
- `ApprovalService.decide()` — now takes the full `AuthenticatedUser`; rejects
  (`403 SELF_APPROVAL_FORBIDDEN`) a non-OWNER deciding an approval whose `requestedBy` is
  themselves; an OWNER proceeds with the decision and gets an `approval.self_approval_override`
  audit row instead.
- `IntegrationServiceAccountSeeder` — Telegram/WhatsApp accounts now seed `MAINTAINER` (was
  `VIEWER`) so they still clear the new `hasRole('MAINTAINER')` gate on task creation;
  `IntegrationScopeFilter` (a servlet filter, runs before method security) remains the actual
  narrow bound regardless of role. Also upgrades an *already-seeded* VIEWER account in place (found
  live against this repo's own long-running dev stack: idempotent seeding otherwise never revisits
  an existing row, so a pre-Phase-15 install would have these accounts stuck on VIEWER forever and
  chat-triggered task creation would start silently 403ing on upgrade).
- `LoginResponse` — gained `userId`/`email`/`role` so the frontend doesn't need to decode the JWT.

**Changed (contracts/frontend):**
- `packages/contracts/openapi.yaml` — version `0.15.0`; `Role`/`User`/`CreateUserRequest`/
  `UpdateUserRequest` schemas, `/users` + `/users/{id}`, `LoginResponse` fields, `403` responses on
  every newly-gated operation.
- `apps/web` — regenerated Angular client (`users.service.ts`). `AuthStore` stores role/email and
  exposes `hasRole(minimum)` (a UI-hiding hint only). "New Task"/"New Project" hide below
  MAINTAINER; approve/reject/request-changes and deploy-approval decisions hide below REVIEWER;
  deploy-trigger hides below OWNER, cancel-run below MAINTAINER.

**Root cause (found live):** `@PreAuthorize` denials throw `AuthorizationDeniedException` from
inside the proxied controller method, which Spring MVC's own exception resolution catches before
it can reach a filter-chain-level `AccessDeniedHandler` — an initial handler wired via
`exceptionHandling().accessDeniedHandler(...)` was never invoked; `GlobalExceptionHandler`'s
catch-all intercepted it first as a 500. Fixed by handling it in a `@RestControllerAdvice` instead.
Separately, `ProjectControllerIntegrationTest`'s `@WithMockUser` (default `ROLE_USER`) stopped
satisfying the new `hasRole('MAINTAINER')` gate; pinned to `@WithMockUser(roles = "MAINTAINER")`.

**Documented deviations:** user creation is direct (operator-set password), not an email invite —
no mail infrastructure exists yet; deactivation/role changes aren't retroactive to already-issued
JWTs (stateless 24h tokens, no revocation list); integration-scoped accounts seeded MAINTAINER
instead of VIEWER (see above); self-approval prohibition generalized to both RUN_RESULT and DEPLOY
approvals. Full detail in `docs/phase-reports/phase-15-report.md`.

**Coverage:** new `RoleAuthorizationMatrixTest` (every MAINTAINER/REVIEWER/OWNER-tier
representative endpoint rejects roles below its minimum, admits roles at/above it via the
hierarchy; read-only endpoints admit all four roles), `ApprovalSelfApprovalTest` (self-approval
rejected + audited; a different REVIEWER can decide; OWNER override + its own audit row; VIEWER
403 + audit row), `UserControllerIntegrationTest` (OWNER-only CRUD, duplicate-email 409,
self-modification blocked, non-OWNER denied), `AuthControllerIntegrationTest` additions
(LoginResponse fields, deactivated-user login block, admin confirmed OWNER+active),
`IntegrationServiceAccountSeederTest` (existing VIEWER account upgraded to MAINTAINER, existing
MAINTAINER account left alone, fresh account seeded as MAINTAINER).

**Verification:** `apps/api` full suite — BUILD SUCCESSFUL, zero regressions. `openapi.yaml` parses
at version `0.15.0` with `/users` paths and all four new schemas present. `apps/web` `npm run
generate:api`, `npm run build`, and `npx ng test --watch=false` all succeeded (14 existing tests,
no regressions). PDF rebuilt and `md5sum`-matched against `docs/Talos_Implementation_Plan.pdf`;
Phase 15's heading dropped `(provisional)`. `git diff --check` clean after stripping generated
trailing whitespace. Source-scoped naming guard returned no matches. Live `docker compose` stack:
rebuilt `talos-api` against the new migration; `V007 - rbac` applied cleanly. Full detail:
`docs/phase-reports/phase-15-report.md`.

## 2026-07-10 — Phase 14: Cost tracking and recommendations

**Ask:** Implement Phase 14 per Section 16 of the plan: extend `AgentResult` with normalized usage
metadata, persist per-run cost, aggregate per provider/project/month behind a dashboard cost
widget, and add advisory-only recommendation signals (suggested agent, cheapest-capable agent,
risk flags) surfaced in the task form and Command Center — never auto-selecting or auto-executing.

**Changed (`packages/agent-adapter-spec`):**
- `adapter.py` — `AgentResult` gained `input_tokens`/`output_tokens`/`total_cost_usd`/`model`, all
  optional and defaulting to `None`.
- `claude_code.py` — parses the documented stream-json `result` event's `usage`/`total_cost_usd`
  and the leading `system`/`init` event's `model`.
- `codex_cli.py`/`cli_agent.py` — `CodexCliAdapter` normalizes `turn.completed`'s `usage.input_tokens`/
  `output_tokens` (real recorded fixture) onto `AgentResult` via new fields on the shared
  `CliAgentAdapter` base; codex never reports a dollar cost, so `total_cost_usd` stays `None` there.
  `OpenCodeAdapter`/`OpenHandsAdapter` deliberately do not fabricate usage capture — neither has a
  verified event schema with a usage/cost field (see phase report deviation 2).

**Changed (orchestrator):**
- `pipeline.py` — reads `input_tokens`/`output_tokens`/`total_cost_usd`/`model` off the agent
  `"result"` event and passes them into the `RUNNING_TESTS` status call.
- `api_client.py` — `update_status()` gained matching optional kwargs, included in the request body
  only when present.

**Changed (backend, `apps/api`):**
- `V006__cost_tracking.sql` — nullable `input_tokens`/`output_tokens`/`cost_usd`/`cost_model` on
  `agent_runs`, plus an aggregation index.
- `AgentRun`/`InternalStatusRequest`/`RunResponse`/`RunDetailResponse` — carry the four new fields
  through the existing pipeline-details merge pattern.
- `RunService.updateStatus()` — forces `costUsd` to `null` for `provider_auth_mode=subscription_local`
  runs regardless of what the adapter reported (Section 13), the one place every status update
  flows through.
- `RunService.resolveAgentKey()` — now falls back to `task.assignedAgentKey` (previously PATCH-only
  and never consulted by `start-run`) before `talos.yaml agents.preferred`; `CreateTaskRequest`
  gained `assignedAgentKey` so the dashboard can set it at task-creation time.
- `dev.talos.costs` (new) — `GET /api/v1/projects/{id}/costs/monthly`: native aggregate query,
  monthly per-agent totals, null (not fabricated-zero) cost for unpriced buckets.
- `dev.talos.recommendations` (new) — `GET /api/v1/projects/{id}/recommendations`: suggested agent
  (best success rate), cheapest-capable agent (lowest avg cost among ≥50% success agents), and risk
  flags (file areas with ≥2 runs whose Section 12.1 policy scan flagged a file). Advisory only.

**Changed (contracts/frontend):**
- `packages/contracts/openapi.yaml` — version `0.14.0`; `Run` usage/cost fields, `CreateTaskRequest.
  assignedAgentKey`, the two new endpoints, and four new schemas.
- `apps/web` — regenerated Angular client (`costs.service.ts`, `recommendations.service.ts`).
  `ProjectStore` gained `loadMonthlyCosts`/`loadRecommendations`; project-detail page gained a cost
  table and recommendations panel; task-form-dialog gained an agent-key selector plus a "use
  suggestion" hint; run-detail shows a run's own usage/cost, labeling subscription runs that have
  tokens but no cost.

**Documented deviations:** kept the existing `AgentResult` name rather than renaming to the plan's
generic "AdapterResult"; chose new `agent_runs` columns over a `run_costs` table (1:1 with a run,
no multi-turn cost breakdown in scope); OpenCode/OpenHands usage capture deferred pending a
verified live-install event schema; no "Command Center" page existed before this phase, so its
widgets landed on the existing project-detail page instead. Full detail in
`docs/phase-reports/phase-14-report.md`.

**Coverage:** new adapter-spec usage-normalization tests (Claude Code, Codex CLI); new orchestrator
pipeline tests (propagation + graceful degradation); new `apps/api` integration tests —
`CostServiceIntegrationTest` (monthly per-provider totals match fixture usage exactly, subscription
runs counted with no fabricated cost), `RecommendationServiceIntegrationTest` (suggested/cheapest
agent selection, risk-flag threshold, graceful degradation with no history), and
`RunControllerIntegrationTest` additions (usage/cost persistence and exposure, subscription cost
nulling end-to-end, task.assignedAgentKey fallback).

**Verification:** `packages/agent-adapter-spec` 75 passed; `apps/orchestrator` 32 passed;
`apps/runner-supervisor` 24 passed (no code changes needed); `apps/api` full suite BUILD
SUCCESSFUL, zero regressions; `openapi.yaml` parses at version `0.14.0` with both new paths and all
four new schemas present; `apps/web` `npm run generate:api`, `npm run build`, and `npx ng test
--watch=false` all succeeded (14 existing tests, no regressions); PDF rebuilt and `md5sum`-matched
against `docs/Talos_Implementation_Plan.pdf`; `git diff --check` clean after stripping generated
trailing whitespace; source-scoped naming guard returned no matches. Not checked: live billing
reconciliation against a real Claude Code/Codex CLI account. Full detail:
`docs/phase-reports/phase-14-report.md`.

## 2026-07-10 — Phase 13: Project memory with pgvector

**Ask:** Implement Phase 13 per Section 16 of the plan (Revision 2.1): create the deferred `memory_documents`/`memory_chunks` tables with pgvector, keep ingestion/embedding/retrieval API-owned, inject project-scoped memory into coding-agent prompts, and prove `memory.enabled: false` preserves pre-Phase-13 prompt output.

**Changed (backend, `apps/api`):**
- `V005__memory.sql` — enables `vector`, creates `memory_documents`, `memory_chunks`, project indexes, source-type constraints, and duplicate suppression on `(project_id, source_type, source_ref, content_hash)`.
- `dev.talos.memory` (new package) — memory document entity/repository, chunker, secret masker, deterministic `EmbeddingProvider`, vector search, public operator ingestion, internal orchestrator ingestion/search, and completed-run ingestion from terminal `COMPLETED` runs.
- `RunService` — calls `MemoryService.ingestCompletedRun()` on the `COMPLETED` transition after the server-side state machine accepts the transition.
- API Testcontainers — all Postgres containers now run `pgvector/pgvector:pg17` as a compatible substitute so Flyway validates the real extension.

**Changed (orchestrator):**
- `ApiClient` — added internal memory document ingestion and search methods under `/internal/v1/projects/{id}/memory/*`.
- `RunPipeline` — for non-`custom-shell` adapters, reads configured `context.docs` from the prepared isolated worktree, sends them to the API for masked/chunked/embedded ingestion, retrieves relevant memory, and passes it into prompt assembly. `memory.enabled: false` skips both ingestion and retrieval.
- `prompt_assembler.py` — inserts `Relevant project memory` after direct project context and before the task. With no memory results, existing prompt output is unchanged.

**Changed (contracts/frontend/infra/docs):**
- `packages/contracts/openapi.yaml` — version `0.13.0`; public memory ingestion plus internal memory ingestion/search schemas and operations.
- `apps/web/src/app/api` — regenerated Angular client. The internal memory operations are tagged as `internal` only to avoid duplicate generated exports between `InternalService` and `MemoryService`.
- `packages/project-config-schema/talos.schema.json` — `memory.enabled` and `memory.prompt_budget_chars`.
- `infra/docker-compose.dev.yml` and `infra/dokploy/docker-compose.prod.yml` — Postgres image changed to `pgvector/pgvector:pg17`.
- `README.md`, `docs/architecture.md`, `docs/deployment.md`, `docs/security-model.md`, `docs/src/talos-implementation-plan.md`, and both plan PDFs — updated for pgvector-backed project memory.
- `docs/phase-reports/phase-13-report.md` — phase gate report.

**Documented deviations:** the plan says embeddings come from a configured BYOK provider, but it does not define the concrete provider/model/protocol. Implemented a deterministic local lexical `HashEmbeddingProvider` behind an `EmbeddingProvider` interface so Phase 13 is self-hosted, testable, and replaceable when the BYOK provider contract is chosen. The prompt budget is character-based (`prompt_budget_chars`) rather than token-based for the same reason: no model/tokenizer contract exists yet.

**Coverage:** API integration tests cover public/internal ingestion, pgvector retrieval, same-project isolation, prompt budget enforcement, completed-run ingestion, memory-disabled behavior, schema validation, chunking, and secret masking. Orchestrator tests cover context-doc ingestion before search, memory prompt injection order, and byte-identical disabled prompts.

**Verification:** targeted API slice (`sg docker -c 'cd /home/paulb/Personal/Talos/apps/api && GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon test --tests "dev.talos.memory.*" --tests "dev.talos.projects.TalosConfigParserTest" --tests "dev.talos.runs.RunServiceRetentionCandidatesTest"'`) — BUILD SUCCESSFUL. Full API suite (`sg docker -c 'cd /home/paulb/Personal/Talos/apps/api && GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon test'`) — BUILD SUCCESSFUL. `UV_CACHE_DIR=/tmp/uv-cache uv run pytest` in `apps/orchestrator` — 30 passed. `python3` YAML parse of `packages/contracts/openapi.yaml` — version `0.13.0`, internal memory document path present. `npm run generate:api` in `apps/web` — succeeded with the known OpenAPI 3.1/model-name warnings. Angular build — initially the default shell's Node 24.11.1 failed the repo's Node gate and sandboxed DNS blocked Google Fonts; rerun with the direct Node 22.23.1 binary and approved network access completed successfully and emitted `dist/talos-web`. `bash docs/src/build-pdf.sh` — first sandboxed run failed on npm DNS; rerun with network approval succeeded, and `md5sum` confirmed `docs/src/talos-implementation-plan.pdf` matches `docs/Talos_Implementation_Plan.pdf`. Compose rendering: `env TALOS_DOCKER_GID=999 docker compose -f infra/docker-compose.dev.yml config --quiet` passed; production Dokploy compose rendered with sample env passed. `git diff --check` passed after stripping trailing whitespace emitted by the generated Angular client. Source-scoped naming guard (`grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude-dir=node_modules --exclude=CLAUDE.md --exclude=AGENTS.md`) returned no matches. Not checked: live run against a production-sized memory corpus or an external embedding provider; the provider contract is intentionally deferred as noted above.

## 2026-07-10 — Phase 12 Track B: Telegram and WhatsApp trigger adapters, phase gate closed

**Ask:** Implement Phase 12 Track B per Section 16 of the plan (Revision 2.1): chat-based task intake and run/approval notifications via Telegram first, then WhatsApp, without weakening any governance rule — approval decisions stay dashboard-only. Close the Phase 12 gate (Track A had already landed) with a phase report.

**Ambiguity resolved (asked, not guessed):** the plan requires the chat trigger JWTs be "least-privilege: task create/read only," but `SecurityConfig` runs pure owner-mode today — every authenticated JWT request passes every check, and real RBAC enforcement is Phase 15, not yet built. Asked via `AskUserQuestion` whether to (a) build a minimal purpose-specific scoped guard now, or (b) defer entirely to Phase 15 and document the gap. Chose (a): a small, additive `IntegrationScopeFilter` restricted to a hard endpoint allow-list, keyed off a new `integration_scoped` JWT claim — not the general Phase 15 RBAC matrix, which stays out of scope.

**Changed (backend, `apps/api`):**
- `dev.talos.auth.AuthenticatedUser` — gained `integrationScoped`; `JwtService.issue()` stamps it true only for the two seeded chat service-account emails (`TalosProperties.telegram/whatsappServiceEmail`); `validate()` reads it back (defaults false for old/other tokens).
- `dev.talos.auth.IntegrationServiceAccountSeeder` (new, mirrors `AdminSeeder`) — seeds the Telegram/WhatsApp service accounts from `TALOS_{TELEGRAM,WHATSAPP}_SERVICE_EMAIL`/`_PASSWORD`, role `VIEWER` (nominal only; real enforcement is the filter below, not this role).
- `dev.talos.auth.IntegrationScopeFilter` (new) — for any request whose principal is `integrationScoped`, allows only `GET`/`POST /api/v1/tasks(/*)`, `GET /api/v1/projects(/*)`, `GET /api/v1/runs/*`, `GET /api/v1/approvals(/*)`, `POST /api/v1/chat/rejected-sender`; everything else 403s (`INTEGRATION_SCOPE_FORBIDDEN`) and writes an `integration.access_denied` audit row. Wired into `SecurityConfig` after `JwtAuthenticationFilter`. Non-integration-scoped requests (e.g. the admin) are completely unaffected.
- `dev.talos.chat.ChatController` (new) — `POST /api/v1/chat/rejected-sender` records a `chat.rejected_sender` audit row (channel + chat ID only, never message contents). Added because talos-api is the sole PostgreSQL writer and a chat adapter has no DB connection of its own, so it has no other way to satisfy "a message from any other chat ID produces ... an audit row."
- `dev.talos.tasks.Task`/`TaskService`/`dto.CreateTaskRequest` — `source` (already `DASHBOARD|WEBHOOK|TELEGRAM|...` per the Section 9 schema comment, but never settable) is now an optional request field, validated against a known set, falling back to `DASHBOARD`.
- `packages/contracts/openapi.yaml` — `CreateTaskRequest.source`, new `/chat/rejected-sender` endpoint + `ChatRejectedSenderRequest` schema.
- `packages/contracts/chat/inbound-command.schema.json` (new) — the normalized command shape both chat adapters parse their channel-specific messages into and validate their parsers against in tests, satisfying "a shared inbound-command schema ... so the API surface does not grow per channel."

**Changed (new apps):**
- `apps/telegram-adapter` — long-polls the Telegram Bot API (no public webhook needed for a self-hosted single-VPS deploy); allow-lists chat IDs before any API call; four deterministic slash commands (`/create_task`, `/task_status`, `/run_status`, `/approvals` — no NLU/LLM layer, per hard constraint 1); a `talos.events` notifier (`talos.notifiers.telegram` queue) formats `approval.requested`/`pr.created`/`run.status.changed` into dashboard-deep-linked chat messages, idempotent on `event_id` via Redis like the orchestrator's own consumers. Idles cleanly (warns, doesn't crash-loop) when unconfigured.
- `apps/whatsapp-adapter` — second implementation of the identical command schema via the WhatsApp Business Cloud API. Unlike Telegram, the Cloud API only supports webhooks, so this is a small FastAPI service: `GET /webhook` handles Meta's verification handshake, `POST /webhook` verifies `X-Hub-Signature-256` over the raw body before anything else runs. Same allow-list/command/notifier logic, own quorum queue (`talos.notifiers.whatsapp`).
- `infra/docker-compose.dev.yml` — both adapters wired in (unconfigured by default: blank bot token/Cloud API credentials so `docker compose up` still works without them); matching seeded service-account env vars added to the `api` service.
- `.github/workflows/ci.yml` — `python` test matrix, `docker-build`, and `container-scan` all gained both new images (built from their own directories — neither depends on `packages/agent-adapter-spec`).

**Coverage:** `apps/telegram-adapter` 31 tests, `apps/whatsapp-adapter` 39 tests (command parsing validated against the shared JSON Schema, handlers against fake API clients, notifier idempotency/fan-out, allow-list + rejected-sender audit behavior, WhatsApp's HMAC signature verification including tampered-body/wrong-secret rejection, FastAPI route tests for the webhook handshake). `apps/api` full suite: 127 tests green, including the new `IntegrationScopeFilterIntegrationTest`.

**Verification:** `sg docker -c "./gradlew test"` in `apps/api` — full suite green, zero regressions. `uv run pytest` green in both new adapters. Naming guard (scoped form) returns nothing. `docker compose -f infra/docker-compose.dev.yml config --quiet` passes; a full `docker compose up -d --build` brought up all 8 services healthy/running. Live against the real running stack: both seeded service-account JWTs decode with `integration_scoped:true`; the Telegram account's JWT can `GET /api/v1/tasks` (200) but `GET /api/v1/integrations` 403s `INTEGRATION_SCOPE_FORBIDDEN`; `talos-telegram-adapter` stays `Up` indefinitely with no bot token configured (idle warning, no restart loop); `talos-whatsapp-adapter` reports healthy on `/health` and correctly 403s an unconfigured verification handshake. `./scripts/smoke.sh` re-run after all changes: PASS. **Not checked:** a live round-trip against a real Telegram bot token or WhatsApp Business Cloud API account (neither available in this environment) — both adapters' provider-facing HTTP calls are covered by unit tests against fakes/signature math, not a live external round-trip; flagged in `docs/phase-reports/phase-12-report.md` for re-verification before first production use. Full detail: `docs/phase-reports/phase-12-report.md`.

## 2026-07-10 — Phase 12 Track A: OpenCode, Codex CLI, and OpenHands adapters + pre-launch capability check

**Ask:** Implement Phase 12 Track A per Section 16 of the plan (Revision 2.1): replace the `OpenCodeAdapter`, `CodexCliAdapter`, and `OpenHandsAdapter` stubs with working adapters in the fixed Section 7.4 order, each preceded by verification of current invocation flags/APIs against provider documentation; add a pre-launch capability check that fails runs with a clear SYSTEM log line; pass the Section 7.2 contract suite per adapter with recorded fixtures; prove the fixture-repo smoke reaches `WAITING_APPROVAL` with only `assigned_agent_key` changed and zero orchestrator/runner code modified.

**Doc verification (first ticket per adapter, per Section 7.4):**
- **OpenCode** (docs + `run` command source, release v1.17.17): `opencode run <prompt> --format json --print-logs` emits one JSON object per line shaped `{"type","timestamp","sessionID",...}` (`tool_use`/`text`/`step_start`/`error`); auth at `~/.local/share/opencode/auth.json`; model/provider and `permission` rules in `opencode.json`.
- **Codex** (locally installed codex-cli 0.144.0 `--help` plus a live recorded run): `codex exec <prompt> --json` prints JSONL `thread.started`/`turn.started`/`item.*`/`turn.completed` events; sessions/config under `CODEX_HOME`.
- **OpenHands** (Software Agent SDK repo, v1.x): agent-server REST — `POST /conversations` (`workspace.working_dir`, `initial_message`, `confirmation_policy`), `GET /conversations/{id}` (execution status enum), paged `GET .../events` (kind-discriminated), `DELETE` to cancel, optional `Authorization: Bearer <session_api_key>`. The exact `StartConversationRequest`/message schema is only published at runtime (`/docs`), noted in the module docstring for re-verification against a deployed instance.

**Changed (backend, `packages/agent-adapter-spec`):**
- `cli_agent.py` (new) — shared subprocess plumbing for CLI adapters (local/containerized spawn, pumps, timeout, process-group kill, transcript, masking) plus the capability check: CLI present (PATH locally; `docker run --rm --network none <image> <cli> --version` in container mode), version ≥ per-adapter floor, credentials present. Failures emit an ERROR event without `stream` metadata — the orchestrator's existing `_stream_for` maps that to a SYSTEM log line — and `result()` reports `success=False` without ever spawning the agent. Claude/custom-shell keep their pre-existing identical plumbing (deliberately not refactored mid-phase; candidate follow-up).
- `opencode.py` — real `OpenCodeAdapter` (api_key only): merges non-bypassable `permission` deny rules (`git commit/push`, `git reset --hard`, `rm -rf`) into the provider-home `opencode.json` without discarding operator config; pins XDG dirs under the provider home so contract 7.2(5) holds; deliberately avoids `--dangerously-skip-permissions`.
- `codex_cli.py` — real `CodexCliAdapter` (api_key + subscription_local): JSONL parser maps `command_execution` items to TOOL_USE + per-line LOGs, `agent_message` to LOG/summary, `file_change` to TOOL_USE, `turn.completed` usage to a SYSTEM line carrying raw token counts in metadata (Phase 14 groundwork). Sandbox flag depends on mode: bare subprocess keeps `-s workspace-write`; inside the Phase 11 container it must use `--dangerously-bypass-approvals-and-sandbox` (see Root cause below).
- `openhands.py` — real `OpenHandsAdapter`, the first non-subprocess adapter: httpx polling client that proxies remote kind-discriminated events into the standard iterator (id-deduped across page re-fetches), synthesizes exit codes (0=finished, 1=error/stuck/cancelled/timeout), `stop()` DELETEs the remote conversation. Connection configured via `<provider_home>/server.json`; capability check probes reachability/auth. Added `httpx` as the package's first runtime dependency (orchestrator/runner-supervisor lockfiles re-resolved).
- `__init__.py` — exports the three new adapters. `gemini_cli.py` remains the only stub (backlog, per Section 7.4 #6).
- Tests: contract suites `tests/contract/test_{opencode,codex_cli,openhands}_contract.py` (fake CLIs / mocked agent-server); fixture-drift parser tests `tests/test_{opencode,codex_cli,openhands}.py`; capability-check tests `tests/test_cli_agent.py` (missing CLI, version floor, missing credentials, auth-mode rejection, container-mode probe via fake docker). Fixtures in `tests/fixtures/` with provenance README — `codex_exec_stream.jsonl` is a real recording from codex-cli 0.144.0; the OpenCode/OpenHands fixtures are constructed from provider source/models and marked for re-recording against live instances.

**Changed (workers/infra/scripts/docs):**
- `workers/base-agent-runner/Dockerfile` — installs `opencode` 1.17.17 and `codex` 0.144.0 (pinned via build args, kept in lockstep with the adapters' `min_cli_version`), including codex's separately-shipped `codex-code-mode-host` sibling binary (without it every codex file edit fails at runtime — found live).
- `scripts/phase12-adapter-smoke.sh` (new) — the Phase 6 fixture-repo smoke parameterized by `TALOS_SMOKE_AGENT_KEY`/`TALOS_SMOKE_AUTH_MODE`; identical flow to `scripts/smoke.sh` with only the agent key changed, plus a real coding prompt instead of the custom-shell command.
- `README.md` — adapter status line updated.

**Root cause (found live, not by unit tests):** two consecutive live codex smoke failures. (1) codex 0.144.0's file-editing tool spawns a sibling `codex-code-mode-host` binary shipped as a separate release asset — the image initially lacked it and every edit failed with ENOENT. (2) With the host present, codex's own bwrap-based `workspace-write` sandbox cannot start inside the Phase 11 per-run container (cap-drop ALL + no-new-privileges forbid the user namespaces bwrap needs), failing every write. Fix: in container mode only, the adapter passes `--dangerously-bypass-approvals-and-sandbox`, which codex documents as intended solely for externally sandboxed environments — the Phase 11 container is exactly that boundary; bare-subprocess mode keeps codex's own sandbox.

**Verification:**
- `uv run pytest` green in `packages/agent-adapter-spec` (66 tests incl. the three new contract suites; container-execution suite run separately via `sg docker`, 6 passed), `apps/orchestrator` (26), `apps/runner-supervisor` (24) — zero orchestrator/runner code changes were needed, per the acceptance criterion.
- Worker image rebuilt; `opencode --version` / `codex --version` / `claude --version` all answer inside it.
- **Live acceptance (codex-cli):** full dev stack up, real ChatGPT-authenticated codex credentials planted in `/var/talos/provider-homes/codex-cli/.codex/`, `TALOS_SMOKE_AGENT_KEY=codex-cli TALOS_SMOKE_AUTH_MODE=subscription_local ./scripts/phase12-adapter-smoke.sh` **PASSED**: run `019f4d7a-e10b-759d-81d7-8c30af89d8c1` executed real Codex inside the per-run container, authored `SMOKE_TEST.txt`, and reached `WAITING_APPROVAL` with the change in `git_changes`.
- **Live capability-check (opencode):** same smoke with `TALOS_SMOKE_AGENT_KEY=opencode` and no credentials planted fails the run cleanly with the SYSTEM log line `capability check failed for opencode: no OpenCode credentials in the provider home: ...` — no mid-run crash, exactly the Section 16 acceptance behavior.
- Baseline `./scripts/smoke.sh` (custom-shell) re-run after all changes: PASS (no regression).
- Naming guard (scoped form) returns nothing; CI already runs the spec-package suite via the existing python matrix, so the contract suite runs for every registered adapter in CI.
- **Not checked:** live runs for `opencode` and `openhands` with real credentials/deployments (none available on this machine) — the opencode fixture and the OpenHands request/event schemas should be re-verified live before first production use, as flagged in `tests/fixtures/README.md` and the module docstrings. Track B (Telegram/WhatsApp) not started; the Phase 12 gate stays open until it lands, so no `docs/phase-reports/phase-12-report.md` yet.

## 2026-07-10 — Root README, production onboarding, and MIT license

**Ask:** Beef up the root README with clearer feature explanation and production deployment guidance, and add an open source license so a GitHub visitor can fork/clone and get Talos running quickly in their own production environment.

**Changed (docs):**
- `README.md` — rewrote the root landing page around product capabilities, non-goals, architecture, local smoke-test setup, Dokploy production quickstart, required production environment variables, worker image build commands, documentation links, and license pointer.
- `docs/deployment.md` — expanded the production runbook to include domain environment variables, the required VPS-side `workers/*` image build step, Docker socket group setup, safer secret-generation guidance, worker image override variables, rollback handling for worker image changes, and backup notes for `TALOS_SECRETS_KEY`.

**Changed (infra):**
- `infra/dokploy/docker-compose.prod.yml` — parameterized Traefik host rules and the web runtime API URL with `TALOS_WEB_DOMAIN` / `TALOS_API_DOMAIN`; explicitly declared the orchestrator's `TALOS_WORKER_IMAGE_*` defaults.

**Changed (license):**
- `LICENSE` — added the MIT License for the project.

**Verification:** `docker compose -f infra/dokploy/docker-compose.prod.yml config --quiet` passed with sample production environment variables; `docker compose -f infra/docker-compose.dev.yml config --quiet` passed with `TALOS_DOCKER_GID=999`; `git diff --check` passed; source-scoped naming guard (`grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude-dir=node_modules --exclude=CLAUDE.md --exclude=AGENTS.md`) returned no matches. The exact local guard without `--exclude-dir=node_modules` was not used as the pass condition because this workspace has ignored dependency content under `apps/web/node_modules` (`git check-ignore` confirms it is ignored) containing a third-party minified match; CI runs from a clean checkout and does not include that ignored directory. Not checked: live deployment to a real Dokploy instance, worker image rebuilds, or full app test suites; this change is docs/compose/license only, so compose rendering and naming/whitespace checks were the relevant validation.

## 2026-07-10 — Plan Revision 2.1: Post-MVP (Phases 12+) expanded into per-feature phase specs

**Ask:** Expand the Post-MVP (Phases 12+) outline in Section 16 of the implementation plan — previously a single sentence — into detailed specifications for each mentioned feature.

**Changed (docs):**
- `docs/src/talos-implementation-plan.md` — title line and preamble bumped to **Revision 2.1** (2026-07-10), noting that no MVP-scope contract (Sections 7–11) changed. Section 16's Post-MVP paragraph replaced with five phase specs in the same Goal/Files/Tasks/Acceptance/Tests format as Phases 0–11:
  - **Phase 12 — Additional adapters and remote triggers** (two tracks): Track A implements `OpenCodeAdapter`, `CodexCliAdapter`, `OpenHandsAdapter` per the Section 7.4 order (flag verification first, contract suite 7.2 green, smoke run passes with only `assigned_agent_key` changed); Track B adds `apps/telegram-adapter` then `apps/whatsapp-adapter` as ordinary REST clients + Section 11 notifier consumers — chat can create tasks (`source=TELEGRAM`) and receive notifications, but approval decisions remain dashboard-only by design.
  - **Phase 13 — Memory**: creates the Section 9.4 deferred tables + pgvector; API owns ingestion/embedding/retrieval (sole-writer constraint); orchestrator injects project-scoped memory as a new Section 7.3 prompt stage; disable switch must reproduce pre-Phase-13 prompts byte-identical.
  - **Phase 14 (provisional) — Cost tracking and recommendations**: normalized usage on `AdapterResult`, per-provider aggregation, null-cost handling for `subscription_local`; recommendations advisory-only, never auto-executing.
  - **Phase 15 (provisional) — Multi-user RBAC enforcement**: enforces the existing OWNER/MAINTAINER/REVIEWER/VIEWER roles server-side with a tested role × endpoint matrix and self-approval prohibition.
  - **Phase 16 (provisional) — MinIO artifact storage**: `ArtifactStore` interface with local-volume default and MinIO implementation; uploads via API only; worker containers never see object-store credentials.
- Phase numbers 12/13 were already fixed by cross-references elsewhere in the plan (Sections 5, 7.4, 9.4); 14–16 are explicitly marked provisional.
- `docs/src/build-pdf.sh` — added an `h4` style (the new sub-phase headings are h4; the stylesheet previously stopped at h3).
- Regenerated `docs/src/talos-implementation-plan.pdf` and the canonical copy `docs/Talos_Implementation_Plan.pdf`.

**Verification:** `bash docs/src/build-pdf.sh` succeeded; `pdftotext | grep` confirmed the new Phase 12–16 headings render in the PDF; `md5sum` confirmed `docs/Talos_Implementation_Plan.pdf` matches the rebuilt `docs/src` output; naming guard (`grep -ri agentos .` scoped form) returns nothing. Not checked: nothing code-level — this change touches only documentation and the PDF build stylesheet.

## 2026-07-10 — Phase 11: Hardening and security

**Ask:** Continue Revision 2 implementation with Phase 11, the last MVP phase: per-run Docker execution using `workers/` images (non-root, no docker.sock, cgroup limits, network policy) behind the unchanged runner HTTP contract; secret-masking sweep; rate limits; retention job verification; dependency + container scanning in CI; `docs/security-model.md` threat model; PostgreSQL backup/restore procedure, executed once as a drill.

**Ambiguity resolved (asked, not guessed):** "runner container cannot reach the Docker daemon" — confirmed via `AskUserQuestion` this means `talos-runner-supervisor` keeps `/var/run/docker.sock` access (needed to launch per-run containers); every per-run container it spawns gets none, verified by a dedicated test.

**Root cause / bugs found (live, not by a unit test):**
1. Debian trixie's `docker.io` package does *not* include the `docker` CLI binary under `--no-install-recommends` — `docker-cli` is only a `Recommends:`. Discovered as `docker: not found` inside a freshly built `talos-runner-supervisor` image; fixed by installing the standalone `docker-cli` package (which, via `apt-cache depends`, turned out to depend only on `libc6` — no `dockerd`/`containerd`/`runc` bloat, and it `Breaks: docker.io`, confirming it's the correct minimal choice, not a workaround).
2. `RunControllerIntegrationTest` (21 test methods sharing one Spring context/Redis instance) logged in fresh via `bearerToken()` on every test, which tripped the new login rate limiter around test #11 once its default (`10` attempts/`60s`) applied. Fixed by caching the bearer token once per class run — a legitimate test-efficiency fix, not a limiter workaround, since the token doesn't need to be re-derived per test.
3. `aquasecurity/trivy-action`, the tool this phase adds *for* security scanning, was itself the target of a March 2026 supply-chain attack (76 of 77 version tags force-pushed to credential-stealing malware). Caught by checking current advisories before wiring it into CI rather than trusting a remembered tag name; every reference is pinned to the confirmed-safe v0.35.0 commit SHA (`57a97c7e7821a5776cebc9bb87c984fa69cba8f1`), not a mutable tag.

**Changed (contracts):**
- `packages/contracts/openapi.yaml` — new `GET /internal/v1/runs/retention-candidates` + `RetentionCandidate`/`RetentionCandidatesResponse` schemas.
- `docs/src/talos-implementation-plan.md` — Section 7.1's `AgentSessionRequest` gained `container: ContainerConfig | None`; Appendix A gained all new Phase 11 env vars across `talos-api`/`talos-orchestrator`/`talos-runner-supervisor`.

**Changed (agent-adapter-spec / container execution):**
- `packages/agent-adapter-spec/src/talos_agent_adapter_spec/container.py` (new) — `ContainerConfig`, `build_docker_run_args` (per-run-subpath volume mounts, cap-drop, resource limits, `--env-file`), `write_env_file`, `ensure_network`, `kill_container`.
- `custom_shell.py`/`claude_code.py` — `start()` branches on `request.container`; containerized path spawns via the container helper; `_kill_process_group()` layers the existing process-group signal (proxied into the container by `docker run`'s own `--sig-proxy`) with an unconditional `kill_container()` safety net.

**Changed (backend — runner-supervisor):**
- `Dockerfile` — stripped Java/Gradle/Claude Code (moved to `workers/`), added Docker CLI, fixed the `talos` user to `uid=1000 gid=1000` (was an unpredictable `useradd --system` UID) to match the `workers/*` images for shared-volume write compatibility.
- `config.py`/`execute.py`/`models.py`/`app.py` — threaded `container_image`/`ContainerConfig` construction (`_volume_subpath` helper) through `/runs/{id}/execute`, calling `ensure_network` before spawning.

**Changed (backend — orchestrator):**
- `config.py` — `worker_image_{base,java,node,python}`, `retention_max_age_days`, `retention_interval_seconds`.
- `pipeline.py` — `_container_image_for(stack_type, settings)` resolves `project.stackType` → image; `RunPipeline` now takes `settings`.
- `runner_client.py` — `execute_run` gained `container_image`.
- `retention.py` (new) — `run_once`/`run_periodically`, wired into `main.py` as a background `asyncio.create_task` alongside the RabbitMQ consumers.

**Changed (backend — talos-api):**
- `dev.talos.runs.RunService.getRetentionCandidates` + `AgentRunRepository.findByStatusInAndCompletedAtBefore` + `PullRequestRepository.findByRunIdInAndStatus` — terminal runs older than `maxAgeDays` with no `OPEN` PR. `InternalRunController` gained the endpoint.
- `dev.talos.auth.LoginRateLimitFilter` (new) — Redis `INCR`+`EXPIRE` fixed-window limiter on `POST /api/v1/auth/login`, wired into `SecurityConfig` ahead of `JwtAuthenticationFilter`. `TalosProperties` gained `loginRateLimitMaxAttempts`/`loginRateLimitWindowSeconds`.

**Changed (infra):**
- `workers/{base-agent-runner,java-runner,node-runner,python-runner}/Dockerfile` (new).
- `infra/docker-compose.dev.yml`, `infra/dokploy/docker-compose.prod.yml` — explicit `name:` on `talos_workspaces`/`talos_provider_homes`; new `external: true` `talos_run_network` (unattached, created lazily by the supervisor); `talos-runner-supervisor` gained the docker.sock bind-mount + `group_add: ["${TALOS_DOCKER_GID}"]` + the new `TALOS_RUN_*` env vars.
- `.github/workflows/ci.yml` — `workers-docker-build` job (sequential, not matrixed, since `java/node/python-runner` build `FROM` a local `base-agent-runner` tag); `smoke` job now also builds `workers/base-agent-runner` and resolves `TALOS_DOCKER_GID` before bringing the stack up (every run is now containerized, so the image and socket access must exist for the smoke test to pass); new report-only `dependency-scan`/`container-scan` Trivy jobs.

**Changed (docs):**
- `docs/security-model.md` — real threat model (was a one-line stub): Section 12.1 recap, the container isolation boundary and its accepted residual risks, secret masking, rate limiting, retention, CI scanning (including the trivy-action supply-chain note), and the backup/restore drill transcript.

**Coverage:** `packages/agent-adapter-spec` — `test_container.py` (8 unit tests, mocked), `test_container_execution.py` (6 real-Docker integration tests: own-workspace read, sibling-run `.env` invisibility, no-socket/no-CLI, secret masking, hard-kill-at-timeout, `ensure_network` idempotency). `apps/runner-supervisor` — `test_execute.py` (`_volume_subpath`). `apps/orchestrator` — `test_pipeline.py` additions (stack-type image resolution + base-image fallback), `test_retention.py` (grouping/cleanup-call behavior via fakes). `apps/api` — `RunServiceRetentionCandidatesTest` (Mockito), `LoginRateLimitIntegrationTest` (real Testcontainers Redis, trips 429).

**Verification:** All four Python test suites green (`uv run pytest`: 29/24/26 passed across `agent-adapter-spec`/`runner-supervisor`/`orchestrator`). `apps/api`: `sg docker -c "./gradlew test"` — 123 passed, 0 failed. All `workers/*` images and the stripped `runner-supervisor` image built and manually smoke-tested. Full live walk against `docker compose -f infra/docker-compose.dev.yml` with `TALOS_DOCKER_GID` resolved: `scripts/smoke.sh` passed end-to-end while a `docker events` capture confirmed a real `talos-run-{run_id}` container (`workers/base-agent-runner:latest`) was created/started/exited(0)/destroyed through the full talos-api → RabbitMQ → orchestrator → runner-supervisor chain — not a manual curl test. Separately verified live: per-run container cannot resolve `talos-postgres`/`talos-api` by name while outbound internet works; no docker.sock/CLI inside a per-run container; `GET /internal/v1/runs/retention-candidates` returns correct real data; `talos_orchestrator.retention.run_once` runs end-to-end inside the live orchestrator container without error; login rate limiter returns 429 on the 11th attempt. Backup/restore drill executed for real (`pg_dump`/`pg_restore` round-trip against a fresh scratch Postgres, all 5 checked tables' row counts matched exactly, admin user intact byte-for-byte) — full transcript in `docs/security-model.md`. Trivy scanning validated locally (`aquasec/trivy:0.69.3` against `talos-runner-supervisor:latest`, exit 0, real CVEs surfaced) before trusting the pinned-SHA CI wiring. Naming guard clean. **Not checked:** a live deploy of the new images to a real Dokploy instance (none available, same limitation as Phase 10); the new CI jobs actually running on GitHub Actions infrastructure (validated by construction against working local build commands, not observed running remotely this session).

## 2026-07-10 — Phase 10: Dokploy integration

**Ask:** Continue Revision 2 implementation with Phase 10: approval-gated deploy trigger for Dokploy, plus Talos's own production deployment runbook.

**Root cause / bug found (live, not by a unit test):** `DeployService.triggerNow` and `ApprovalService.decide()`'s public entry points were `@Transactional`. `deployProvider.trigger(...)` is an external HTTP call; when it threw, Spring rolled back the *whole* enclosing transaction, including the "mark FAILED" write `triggerNow` made in its own catch block and, in the `ApprovalService` path, the approval's `APPROVED` status itself — a human's approval would silently revert to `PENDING` whenever the deploy trigger failed. Fixed by removing `@Transactional` from `requestDeploy`, `triggerNow`, and `approve`/`reject`/`requestChanges`; each write already commits independently via Spring Data's per-method transactions, and `RunService.transitionRun` (a separate bean) keeps its own atomicity for the `RUN_RESULT` run-transition path.

**Changed (contracts):**
- `packages/contracts/openapi.yaml` — new `ProjectEnvironment`/`DeployStatus`/`DeployTriggerResponse` schemas; `POST/GET /runs/{id}/deploy`, `GET /projects/{id}/environments`; `GET /approvals` gained an optional `type` filter; `Approval` gained `environment`; version bumped to `0.10.0`. Also fixed two more unquoted-colon-in-description YAML bugs (same class of latent issue as Phase 9's `runner-api.yaml` fix) that broke `openapi-generator` parsing.
- `packages/contracts/events/deploy.requested.json`, `deploy.completed.json`, `deploy.failed.json` (new) — Section 11's three deploy events, all produced solely by talos-api (resolves the plan's ambiguous "API/orchestrator" producer — see phase report deviation 3).

**Changed (backend):**
- `apps/api/src/main/resources/db/migration/V004__deploy.sql` — `project_environments` (Section 9.4 explicitly deferred this table to Phase 10) + `approvals.environment`.
- `dev.talos.integrations` — `DeployProvider`/`DokployDeployProvider` (Dokploy REST client, verified against current docs via Context7), `ProjectEnvironment`/`ProjectEnvironmentRepository`, `DeployService` (`requestDeploy`, `triggerNow`, `getStatus`), `DeployStatusPoller` (new `@Scheduled` job mirroring `RunReaper`). `IntegrationService` gained `resolveDokployCredentials()` and a `dokploy` branch in `test()`.
- `dev.talos.approvals.ApprovalService` — `decide()` now branches on `approvalType` (`RUN_RESULT` unchanged; `DEPLOY` calls `DeployService.triggerNow` on approve only); `ApprovalRepository` gained a unified `search(status, runId, approvalType, pageable)` query replacing three separate derived-query overloads.
- `dev.talos.runs.RunController` — `POST/GET /{id}/deploy`. `dev.talos.projects.ProjectController` — `GET /{id}/environments`.

**Changed (frontend):**
- `run.store.ts`/`run-detail.page.ts`/`.html` — a Deploy section on `RunDetailPage`, visible once `COMPLETED`: status display, Deploy button, inline approve/reject (reusing Phase 8's `ApprovalActionDialogComponent`) for a pending `DEPLOY` approval.
- Angular client regenerated from the updated `openapi.yaml`.

**Changed (docs/infra):**
- `docs/deployment.md` — real operator runbook (was a Phase-0 placeholder): prerequisites, Dokploy Compose app setup, full Appendix A env var mapping, GitHub/Dokploy integration setup via `curl`, rollback procedure.
- `infra/dokploy/docker-compose.prod.yml` (new) — production reference composition, all eight Section 18 services, importable via Dokploy's "Compose" app type.

**Coverage:** `DokployDeployProviderTest` (mock HTTP server — trigger success/400/401, all four Dokploy status mappings), `DeployStatusPollerTest` (direct-call style mirroring `RunReaperTest`), `DeployControllerIntegrationTest` (422 on non-`COMPLETED`/no-config, the production approval-gate proof with a Mockito `never()` provider-call assertion, staging auto-deploy with no approval row, and the transaction-boundary regression test).

**Verification:** `apps/api`: `sg docker -c "./gradlew test"` — full suite green (multiple full runs across the transaction-boundary fix). `apps/web`: `npm run build` and `npx ng test --watch=false` — both green. `docker compose -f infra/docker-compose.dev.yml up -d --build` — live walk: configured a `dokploy` integration, created a project with a `production` deploy config, walked a run to `COMPLETED`, requested a deploy (created a `PENDING` `DEPLOY` approval, confirmed the provider was never called), approved it against an intentionally-unreachable Dokploy URL (502), and confirmed — after the fix — the approval stayed `APPROVED` and the environment correctly recorded `FAILED` rather than silently reverting. Grepped talos-api's logs for the fake Dokploy API key: zero matches. Naming guard clean. **Not checked:** live deploy against a real Dokploy instance (none available); interactive browser verification of the Deploy section UI (no browser automation tool available this session).

## 2026-07-09 — Phase 9: Git push and PR workflow

**Ask:** Continue Revision 2 implementation with Phase 9: approval → commit → push → GitHub PR, recorded and linked — the orchestrator consuming `approval.decided` (published since Phase 8 but never consumed), a runner-supervisor push step, and talos-api-owned GitHub PR creation.

**Changed (contracts):**
- `packages/contracts/openapi.yaml` — new `Integration`/`IntegrationCreateRequest`/`TestIntegrationResponse`/`PullRequest`/`PullRequestStatus`/`GitTokenResponse`/`InternalPullRequestRequest` schemas; `GET/POST /integrations`, `POST /integrations/{id}/test`, `GET /runs/{id}/pull-request`, `GET /internal/v1/runs/{id}/git-token`, `POST /internal/v1/runs/{id}/pull-request`; version bumped to `0.9.0`.
- `packages/contracts/runner-api.yaml` — new `POST /runs/{id}/push` + `PushRequest`/`PushResponse`; version bumped to `0.2.0`. Also fixed a pre-existing YAML syntax bug (an unquoted `{type: log, message}` in a description broke this file for any real parser — never caught because nothing validates it against a schema the way `openapi.yaml` is validated against springdoc).
- `packages/contracts/events/pr.created.json` (new) — Section 11's `pr.created` (API → notifiers).

**Changed (backend):**
- `dev.talos.secrets.SecretService` (new) — AES-256-GCM encrypt/decrypt on top of the `SecretValue`/`IntegrationCredential` entities that existed since Phase 2 with no service layer.
- `dev.talos.integrations` (new package) — `GitHubClient`/`GitHubClientImpl` (`java.net.http.HttpClient`), `GitHubRepoRef` (parses owner/repo from a GitHub `repo_url`), `IntegrationService`/`IntegrationController`, `GitCredentialsService` (409s unless `run.status == APPROVED`), `PullRequestService` (PR body template, stores `PullRequest`, publishes `pr.created`, completes the run). `PullRequest`/`PullRequestRepository`/`PullRequestStatus` already existed since Phase 2 as unused scaffolding.
- `dev.talos.runs.InternalRunController` — `GET /{id}/git-token`, `POST /{id}/pull-request`. `dev.talos.runs.RunController` — `GET /{id}/pull-request`. `dev.talos.runs.RunService` — `getPullRequest()`.
- `infra/docker-compose.dev.yml` — fixed `TALOS_SECRETS_KEY`'s dev placeholder, which decoded to 35 bytes, not a valid AES-256 key length (16/24/32); caught by `SecretServiceTest`/`IntegrationControllerIntegrationTest` failing with a 500 the first time they ran against it.

**Changed (runner supervisor):**
- `git_push.py` (new) — stage/commit (preserving any commit the agent already made)/push; token delivered only via a transient `GIT_ASKPASS` env var, never in the remote URL, on disk, or logged; non-fast-forward rejection returns `needs_rebase` rather than raising. `POST /runs/{id}/push` (`app.py`).

**Changed (orchestrator):**
- `main.py` — binds `talos.orchestrator.approvals` to routing key `approval.decided` (declared since Phase 8, never bound to a queue before now).
- `pipeline.py` — `handle_approval_decided`: no-ops for non-`APPROVED` decisions, guards the run is still `APPROVED` (redelivery race, mirrors the existing `QUEUED` check), records `PUSH`/`PR` steps, fetches the token, pushes, opens the PR (which completes the run server-side), or flags `FAILED`/`NEEDS_REBASE` on a rejected push.
- `api_client.py`/`runner_client.py` — `get_git_token`, `create_pull_request`, `push`.

**Changed (frontend):**
- `run.store.ts`/`run-detail.page.html` — fetches and displays the PR link once a run reaches `COMPLETED`.
- Angular client regenerated from the updated `openapi.yaml`.

**Coverage:** `SecretServiceTest` (round-trip, distinct nonce/ciphertext per call), `GitHubClientTest` (embedded `com.sun.net.httpserver.HttpServer` mock — success, 401, PR-create success, PR-create 422 without leaking the token in the exception message), `IntegrationControllerIntegrationTest` (create/list/test, secret-never-in-response masking test), `RunControllerIntegrationTest` additions (both 409 "unapproved run cannot push" proofs; the full approve → git-token → push → pull-request → `COMPLETED` flow with a real `pull_requests` row), `EventPublisherIntegrationTest` addition (`pr.created` validates against its schema). `test_git_push.py` (commit+push happy path, agent-commit preserved, non-fast-forward → `needs_rebase`, refuses the default branch) plus two new runner-supervisor endpoint tests. `test_pipeline.py` additions (happy path, `REJECTED` no-op, non-`APPROVED` race guard, `NEEDS_REBASE` path).

**Verification:** `apps/api`: `sg docker -c "./gradlew test"` — full suite green. `apps/orchestrator`: `uv run pytest` — 22/22 passed. `apps/runner-supervisor`: `uv run pytest` — 22/22 passed. `apps/web`: `npm run build` and `npx ng test --watch=false` — both green. `docker compose -f infra/docker-compose.dev.yml up -d --build` — live walk against a real local bare git repo (mounted into the runner-supervisor's own workspace volume): confirmed `git-token`/`pull-request` both 409 before approval, approved the run, confirmed the branch was actually pushed to the bare repo with the correct commit sha (verified via `git log` on the bare repo directly), and confirmed the PR step correctly failed (non-GitHub fixture URL) rather than hanging, landing the run on `FAILED` with a clear message. Grepped all four log surfaces (talos-api, talos-orchestrator, talos-runner-supervisor, persisted `agent_run_logs`) for the raw credential used in the walk: zero matches. Naming guard clean. **Not checked:** live PR creation against a real GitHub repository (no scratch repo/PAT available — explicitly optional per the plan; covered by the mock-server test instead); interactive browser verification of the PR-link UI addition (no browser automation tool available this session).

## 2026-07-09 — Phase 8: Review and approval flow

**Ask:** Continue Revision 2 implementation with Phase 8: Review Center + approvals gating everything downstream — post-run policy scan, auto-created approvals on `WAITING_APPROVAL`, approve/reject/request-changes, task status sync, audit, and the `/runs/:id`/`/review/:runId` Angular pages.

**Root cause / gap found:** `POST /internal/v1/runs/{id}/status` (orchestrator-only, service-token auth) accepted `APPROVED`/`REJECTED` as target statuses with no restriction — Section 8.2 marks that edge "API, human decision" only, so the orchestrator's service token alone could have pushed a run past human review. Closed by rejecting both targets there with `422 APPROVAL_REQUIRED_FOR_TRANSITION`; they're now reachable exclusively through the new approval endpoints.

**Changed (contracts):**
- `packages/contracts/openapi.yaml` — `GET /runs/{id}/diff`, `GET/POST /approvals*` (list with new `runId` filter, detail, approve/reject/request-changes), `InternalChangesRequest` gained optional `diffPatch`; new `Diff`/`GitChangeSummary`/`Approval`/`ApprovalDetail`/`ApproveRequest`/`RejectRequest`/`RequestChangesRequest`/`PageApproval`/`ApprovalStatus` schemas; version bumped to `0.8.0`.
- `packages/contracts/events/approval.requested.json`, `approval.decided.json` (new) — Section 11's two approval events.

**Changed (backend):**
- `dev.talos.policy` (new package) — `PolicyRules`, `PolicyConfig` (bundled classpath `policy.yaml` default, `TALOS_POLICY_FILE` override), `PolicyMatcher` (gitignore-style file globs + command substrings), `PolicyScanService` (the Section 12.1 post-run scan).
- `dev.talos.approvals` — `ApprovalService`/`ApprovalController` (new); `Approval.decide()` (new mutator); `ApprovalRepository` gained paged `findByStatus`/`findByRunId`/`findByStatusAndRunId`.
- `dev.talos.runs.RunService` — `transitionRun()` now runs the policy scan and auto-creates the `PENDING` approval when the target status is `WAITING_APPROVAL`; `updateStatus()` rejects `APPROVED`/`REJECTED` targets (see Root cause); `recordChanges()` persists `diffPatch`; new `getDiff()`.
- `dev.talos.runs.AgentRun`/`GitChange` — new `diff_patch`/`matched_pattern` columns (`V003__review_and_approvals.sql`), documented as a Phase 8 DDL addition (Section 9.2 predates this phase and had no column for either).
- `dev.talos.runs.dto` — `GitChangeResponse` (new; output-only counterpart to `GitChangeDto` — see Deviations in the phase report for why they're split), `DiffResponse` (new).

**Changed (orchestrator):**
- `api_client.py`/`pipeline.py` — `record_changes` now forwards the runner supervisor's already-computed unified diff text instead of discarding it.

**Changed (frontend):**
- `apps/web/src/app/runs/` (new) — `RunStore`, `RunEventStreamService` (fetch + `ReadableStream` SSE reader — native `EventSource` can't send the `Authorization` header this endpoint requires), `RunDetailPage` (`/runs/:id`).
- `apps/web/src/app/approvals/` (new) — `ApprovalStore`, `ApprovalActionDialogComponent` (Section 15's confirmation-dialog UX rule), `ReviewPage` (`/review/:runId`).
- `task-drawer.component.html` — task's run list now links to `/runs/:id` (previously dead-end text).
- Angular client regenerated from the updated `openapi.yaml`.

**Coverage:** `PolicyMatcherTest` (one case per pattern class), `ApprovalControllerIntegrationTest` (auto-creation, the `.env` RISK_FLAGGED-with-matched-pattern scenario, approve/reject/request-changes, double-decision 409, task status sync), `RunControllerIntegrationTest` updated to reach `APPROVED` via the approval endpoint and to prove the internal-endpoint bypass now 422s, `test_pipeline.py` updated for diff-text forwarding, `review.page.spec.ts` (new — approve/cancel/reject-requires-notes/request-changes dialog flows).

**Verification:** `apps/api`: `sg docker -c "./gradlew test"` — all 13 test classes green. `apps/orchestrator`: `uv run pytest` — 18/18 passed. `apps/web`: `npm run build` and `npx ng test --watch=false` — both green. `docker compose -f infra/docker-compose.dev.yml up -d --build` — Flyway applied `V003` cleanly against the existing dev database; a full live curl walk against the running stack (start run → RUNNING_AGENT → ... → REVIEWING → record a `backend/.env` change → WAITING_APPROVAL → approve) confirmed all three acceptance criteria: `reviewStatus` `RISK_FLAGGED` with `matchedPattern: ".env*"` on the diff endpoint, the internal-endpoint bypass attempt returned 422, and approving moved the run to `APPROVED`. Naming guard clean. **Not checked:** interactive browser verification of the two new pages — no browser automation tool was available this session; see phase report.

## 2026-07-09 — Phase 7: Claude Code adapter and prompt assembly

**Ask:** Continue Revision 2 implementation with Phase 7: implement `ClaudeCodeAdapter`, the Section 7.3 prompt assembler, provider-home bootstrap documentation, and the real-agent acceptance.

**Changed (adapter/orchestrator):**

- `packages/agent-adapter-spec/src/talos_agent_adapter_spec/claude_code.py` — replaces the stub with a provider-isolated headless adapter: current stream-JSON CLI invocation, output parsing, secret masking, native deny settings, transcript capture, and timeout/cancel kill-tree behavior.
- `apps/orchestrator/src/talos_orchestrator/prompt_assembler.py` and `pipeline.py` — assemble and persist the Section 7.3 four-part prompt for real agents while preserving `custom-shell`'s documented literal-command semantics.
- `apps/runner-supervisor/Dockerfile` — supplies Claude Code from Anthropic's signed APT repository, Temurin Java 21, and Gradle 9.5.1 for real Spring Boot/Kotlin DSL workspaces; Maven is intentionally not installed.
- `scripts/phase7-live-smoke.sh` — a repeatable authenticated Spring Boot 4.1 fixture acceptance using Gradle Kotlin DSL. It avoids a host `jq` dependency, checks the task's focused test and actual `/hello` source, and excludes Gradle build outputs from review diffs.
- `docs/provider-auth.md` — documents both isolated provider-home authentication modes.

**Coverage:** Added Claude's six Section 7.2 adapter-contract checks using a deterministic fake CLI, recorded stream-JSON parser tests, prompt-assembly units, and audit-prompt pipeline coverage.

**Verification:** `UV_CACHE_DIR=/tmp/talos-uv-cache uv run pytest` in `packages/agent-adapter-spec` — 15/15 passed; the same command in `apps/orchestrator` — 18/18 passed. `bash -n scripts/phase7-live-smoke.sh` and `git diff --check` passed. The local runner image was rebuilt and verified with OpenJDK 21.0.11, Gradle 9.5.1, and Claude Code 2.1.197. Live run `019f491c-15cb-7668-9c35-2e0c7ddc6186` reached `WAITING_APPROVAL`; its completed agent and test steps produced `HelloController.java` and `HelloControllerTest.java`, and live workspace inspection verified `@GetMapping("/hello")` returning `Hello, Talos!`.

## 2026-07-09 — Phase 6: orchestrator and runner supervisor (dummy flow)

**Ask:** Implement Section 16 Phase 6: full pipeline with `CustomShellAdapter` — consume → workspace → execute → tests → diff → `WAITING_APPROVAL`. `apps/orchestrator`, `apps/runner-supervisor`, `packages/agent-adapter-spec` (ABC + `CustomShellAdapter` + contract tests), `scripts/smoke.sh`.

**Changed (contracts):**
- `packages/contracts/openapi.yaml` — `POST /api/v1/runs/{id}/cancel`; `POST /internal/v1/runs/{id}/changes`; `InternalStatusRequest` gained optional `testStatus`/`workspacePath`/`branchName`/`prompt`/`summary`/`exitCode` fields (see phase report Deviations); version bumped to `0.6.0`.
- `packages/contracts/runner-api.yaml` (new) — the runner supervisor's own OpenAPI file, Section 10.5.
- `packages/contracts/events/run.cancel.requested.json` (new) — a Phase 6 extension of Section 11's event table, mirroring `approval.decided`'s producer→consumer shape (see phase report Deviations for why).

**Changed (backend):**
- `dev.talos.runs.GitChange`/`GitChangeType`/`GitChangeRepository` — pre-existing since Phase 2 (entity scaffolding only); `RunService.recordChanges()` (new) is their first real writer.
- `dev.talos.runs.RunService` — `cancel()` (transitions to `CANCELLED` via the existing validator, publishes `run.cancel.requested`), `recordChanges()`, `updateStatus()` now takes the full `InternalStatusRequest` and applies its optional fields via `AgentRun.applyPipelineDetails()` (new mutator) before transitioning.
- `dev.talos.runs.RunController` — `POST /{id}/cancel`. `dev.talos.runs.InternalRunController` — `POST /{id}/changes`.
- `dev.talos.runs.dto` — `GitChangeDto`, `InternalChangesRequest`, `RunCancelRequestedPayload`; `InternalStatusRequest` extended.

**Changed (agent-adapter-spec):**
- `adapter.py` — `AgentEventType`/`ProviderCapabilities`/`AgentSessionRequest`/`AgentEvent`/`AgentResult`/`AgentAdapter` (Section 7.1, verbatim).
- `custom_shell.py` — `CustomShellAdapter`: real subprocess execution of `request.prompt` as a shell command, process-group kill-tree `stop()`, timeout enforcement, env-value masking in every emitted event, transcript written under `provider_home` (never the worktree).
- `claude_code.py`/`opencode.py`/`codex_cli.py`/`openhands.py`/`gemini_cli.py` (new) — `NotImplementedError` stubs per Section 7.4's fixed order, each naming its assigned phase.
- `registry.py` — `ADAPTERS` + `get_adapter_class()`.
- `tests/contract/test_custom_shell_contract.py` (new) — the Section 7.2 six-point suite.

**Changed (runner-supervisor):**
- `config.py`, `models.py` (pydantic request/response schemas), `app.py` (FastAPI routes for every `runner-api.yaml` endpoint), `workspace.py` (clone-or-fetch, `git worktree add -B`, copy filter), `diff_capture.py` (numstat/name-status parsing into `GitChange`s + `diff.patch` artifact), `execute.py` + `run_registry.py` (in-memory adapter registry, ndjson event streaming), `test_command.py` (configured test command, ndjson streaming), `process_utils.py` (shared kill-tree helper).
- `Dockerfile` — `mkdir`+`chown` `/var/talos/{workspaces,provider-homes}` before `USER talos` (fixes a named-volume ownership bug — see phase report); `ENTRYPOINT` uses `uv run --no-sync` (fixes a runtime crash-loop — see phase report).

**Changed (orchestrator):**
- `config.py`, `api_client.py` (`/internal/v1` httpx client, extended with the new optional `update_status` fields), `runner_client.py` (runner-api.yaml httpx client), `locks.py` (`RunLock`: Redis `SET NX EX` + Lua compare-and-delete release), `adapters.py` (capability lookup only), `log_batcher.py` (50-line/2s batching), `pipeline.py` (`RunPipeline`: full Section 8.1/8.2 state walk, crash-recovery poisoning check, cancel forwarding, best-effort failure reporting — see phase report Deviations for a race this last point fixes), `main.py` (`aio-pika` bootstrap: `talos.events`/`talos.events.dlx`/`talos.dlq`, two quorum queues with `x-delivery-limit: 3`, Redis-cached `event_id` idempotency).
- `Dockerfile` — same two fixes as runner-supervisor's (`uv run --no-sync`; no volume-ownership issue here since orchestrator mounts none).
- `pyproject.toml` (orchestrator) — added `redis`; both Python apps' `pyproject.toml` gained `asyncio_mode = "auto"`.

**Bug found and fixed — `AuthControllerIntegrationTest.actuatorHealth_isPublic()` had been silently failing since Phase 5.** It pointed RabbitMQ/Redis at unreachable `localhost` addresses on the Phase-1-era assumption that those integrations "aren't load-bearing until later phases" — true until Phase 5 removed the `management.health.{rabbit,redis}.enabled: false` overrides, after which `/actuator/health` started actually dialing them and has returned 503 in this one test ever since (only surfaced now because this is the first time this session ran the *full* `./gradlew build` rather than a targeted test subset). Fixed by giving it real `RabbitMQContainer`/Redis `GenericContainer` instances, matching every other integration test's pattern.

**Two container-startup bugs and one live race condition, found only by running the real stack — see phase report Deviations for full detail:** (1) both Python `Dockerfile`s' `uv run <entrypoint>` crash-looped as the non-root user trying to reconcile dev-dependencies at runtime — fixed with `--no-sync`. (2) the runner supervisor's named volumes were seeded root-owned, causing `PermissionError` on first `git clone` — fixed with an explicit `mkdir`+`chown` before `USER talos`. (3) cancelling a run mid-agent-execution raced the pipeline's own failure-reporting against the API's terminal-state guard, producing an unhandled exception (though the DB state stayed correct) — fixed by making that report best-effort.

**Changed (tests):**
- Python: `packages/agent-adapter-spec` 7 tests (6 new — contract suite). `apps/runner-supervisor` 16 tests (15 new — workspace/diff/execute/stop/cleanup). `apps/orchestrator` 15 tests (14 new — `LogBatcher`, idempotency, `RunPipeline` happy-path/lock/failure/poisoned-run/cancel/race-regression).
- Java: `RunControllerIntegrationTest` +4 (cancel legal/terminal, internal changes, optional pipeline details), `EventPublisherIntegrationTest` +1 (`run.cancel.requested` schema validation), `AuthControllerIntegrationTest` fixed (see bug above, test count unchanged).

**Coverage:** Backend (Java): 77 tests across 12 classes (5 new). Python: 38 tests across 3 projects (35 new: 6 + 15 + 14). Frontend: unchanged (10 tests, 3 files) — Phase 6 has no UI.

**Verification:** All four module test suites green (`apps/api` 77/77, `packages/agent-adapter-spec` 7/7, `apps/runner-supervisor` 16/16, `apps/orchestrator` 15/15). `npm run build` unaffected. All four Docker images build independently. Full live verification against `docker compose -f infra/docker-compose.dev.yml up -d --build` (all six services healthy): `scripts/smoke.sh` passed twice, including once from a fully clean slate, proving a real `git clone`/worktree/branch/subprocess-execution/test-run/diff-capture/`git_changes` walk to `WAITING_APPROVAL` over the real Docker network with zero mocks; a live cancel-mid-run scenario run twice (pre- and post-fix) confirmed via `docker top` that the process tree is actually gone and the run correctly stays `CANCELLED`. Lock contention was not reproduced live — a single sequential (`prefetch=1`) orchestrator consumer can never actually observe two runs racing for one project/branch lock, so this is proven by the mocked `RunLock` unit test instead (see phase report for the full reasoning). `grep -ri agentos` — zero results. CI still hasn't run on GitHub Actions (no remote configured); the new `smoke` CI job is consequently unverified against real Actions runners.

**Notes:**
- `CustomShellAdapter` treats `request.prompt` as a literal shell command for this phase (Section 7.3's real prompt assembler is Phase 7); the orchestrator passes the task's `description` straight through. Consistent with the adapter's own "No AI" description.
- None of the 38 new automated tests would have caught any of the three real bugs found this phase (two Docker/container issues, one live race) — all three are specifically about real OS/container/process behavior outside what mocks and fakes model. Same lesson as Phase 5's report: keep doing both kinds of verification.
- `run.cancel.requested` is a documented, judgment-call extension of Section 11's event table (not in the plan verbatim) — modeled as a structural mirror of the existing `approval.decided` row rather than invented from scratch; full reasoning in the phase report.

**Known blockers / follow-ups:**
- `POST /internal/v1/runs/{id}/artifacts`, `GET /api/v1/runs/{id}/diff` — deferred; no durable artifact storage target exists yet (MinIO is post-MVP) and nothing needs to *serve* a diff until Phase 8's Review Center.
- `POST /api/v1/runs/{id}/rerun-tests`, `GET /api/v1/projects/{id}/runs` — still not in any phase's task list.
- `/workspaces/cleanup` has no scheduled caller yet; it only ever deletes explicitly-named run ids passed by whoever calls it, and nothing calls it currently.
- Real secret injection (Section 12.2), policy scanning/risk flagging (Section 12.1) — both explicitly later phases (8/11); `env={}` and `review_status=CLEAN` always, this phase.
- No UI — no "start run" or "cancel run" button in `apps/web` yet; both are only reachable via direct API calls (as `scripts/smoke.sh` does).
- CI still hasn't run on GitHub Actions (no remote configured).

## 2026-07-09 — Phase 5: agent run lifecycle (API side)

**Ask:** Implement Section 16 Phase 5: run records + state machine, event publishing to RabbitMQ, internal service-token API, Redis-backed SSE log streaming, scheduled reaper. Explicitly API-side only — no orchestrator, no execution, no diff capture.

**Changed (contracts):**
- `packages/contracts/openapi.yaml` — `runs` tag (`POST /tasks/{id}/start-run`, `GET /runs`, `GET /runs/{id}`, `GET /runs/{id}/logs`, `GET /runs/{id}/events/stream`) and `internal` tag (`POST /internal/v1/runs/{id}/{status,steps,logs}`, `GET /internal/v1/runs/{id}/context`) with per-operation `servers`/`security` overrides to the `/internal/v1` server and a new `serviceToken` scheme.
- `packages/contracts/events/task.run.requested.json`, `run.status.changed.json` (new) — first two event JSON Schemas, envelope + payload per Section 11.

**Changed (backend):**
- `dev.talos.events` (new module) — `EventEnvelope<T>` (snake_case via `@JsonProperty`), `RabbitConfig` (`talos.events` topic exchange + `JacksonJsonMessageConverter`, the Jackson-3-native converter — not the legacy `Jackson2JsonMessageConverter`), `EventPublisher`.
- `dev.talos.runs.RunTransitionValidator` (new) — Section 8.2's state machine as data (happy path, FAILED-from-any-non-terminal, CANCELLED-from-QUEUED..WAITING_APPROVAL, REJECTED-from-WAITING_APPROVAL) plus the per-state timeout `Duration` table.
- `dev.talos.runs.RunService` (new) — `transitionRun()` is the one path every status change flows through (validates, sets `timeout_at`, audits `run.status.changed` — or `run.transition.rejected` on an illegal attempt — applies the task↔run mapping, publishes to RabbitMQ + the SSE broadcaster). `startRun()` resolves `agentKey`/`authMode` defaults from the active `talos.yaml`, enforces `409 ACTIVE_RUN_EXISTS`, publishes `task.run.requested`.
- `dev.talos.runs.RunController` (new, public), `dev.talos.runs.InternalRunController` (new, service-token), `dev.talos.tasks.TaskController` gained `POST /{id}/start-run`.
- `dev.talos.auth.InternalTokenAuthenticationFilter` + `SecurityConfig` gained a second `@Order(1)` `SecurityFilterChain` matched to `/internal/v1/**` (separate from the JWT chain).
- `dev.talos.runs.RedisConfig` (`RedisMessageListenerContainer` bean) + `RunEventBroadcaster` — publishes log/status/step events to Redis channel `talos:run:{id}:logs` (one channel, `{type, data}` envelope); SSE endpoint backfills from Postgres then relays live; 15s heartbeat via `@Scheduled`.
- `dev.talos.runs.RunReaper` (new), `@Scheduled(fixedDelay = 60000)`, fails expired runs via `transitionRun()`. `@EnableScheduling` added to `TalosApiApplication`.
- `AgentRun.transitionTo()`, `AgentRunStep.complete()` — first mutations on these entities (same "first phase with a real update path flips `updated_at` to `updatable=true`" pattern as every prior phase's first-touched entity). New repository finders: paged run queries, `existsByTaskIdAndStatusNotIn`, `findByStatusInAndTimeoutAtBefore`, latest-open-step lookup, paged log-after-sequence.
- `dev.talos.runs.dto` (new) — `StartRunRequest`, `RunResponse`/`RunDetailResponse`/`StepResponse`/`LogEntryResponse`/`RunContextResponse`, internal request DTOs, `TaskRunRequestedPayload`/`RunStatusChangedPayload` event payloads.
- `apps/api/build.gradle.kts` — added `testcontainers-rabbitmq:2.0.5`; new `copyEventSchemas` Gradle task (same dedicated-`generated-resources`-dir pattern as `copyTalosSchema`, added to the *test* source set) puts `packages/contracts/events/*.json` on the test classpath for schema-validation tests.
- `application.yml` — re-enabled the RabbitMQ/Redis `/actuator/health` indicators (both are load-bearing as of this phase; were explicitly disabled since Phase 1/2 pending exactly this).

**Bug found and fixed — `AuditService.record()` needed `REQUIRES_NEW` transaction propagation, not the default.** `internalStatus_illegalTransition_returns422AndWritesAuditEvent` failed: the audit row for a rejected transition was recorded inside the same `@Transactional` method that then throws `ApiException` to produce the 422, and Spring's default rollback-on-unchecked-exception silently rolled the audit insert back too — losing exactly the row Section 8.2 requires ("rejects illegal ones with HTTP 422 plus an audit event"). Fixed by switching `AuditService.record()` to `Propagation.REQUIRES_NEW`, so every audit row commits independently of its caller's transaction outcome — the correct general behavior for a write-only audit trail (Section 12.2), not a Phase-5-specific patch. Re-ran the full existing suite after the change; no regressions.

**Changed (tests):**
- `RunTransitionValidatorTest` — exhaustive 12×12 matrix + explicit happy-path/skip/terminal/cancel-boundary/timeout assertions.
- `RunControllerIntegrationTest` (13 cases) — start-run success/config-default-agentKey/no-config-422/409-active-run; full CREATED→...→COMPLETED walk asserting the task-side mapping at each stage; FAILED→BLOCKED; step start-then-complete updates one row, not two; log ingest→read-back; internal context; service-token auth (missing/wrong/correct); list/get/404.
- `EventPublisherIntegrationTest` (Testcontainers RabbitMQ) — consumes the real published message off a test-declared queue and validates it against both JSON Schema files.
- `RunEventStreamIntegrationTest` — real running server (`webEnvironment = RANDOM_PORT`) + raw streaming `java.net.http.HttpClient` (MockMvc can't drive a live stream concurrently with another request); a log POSTed internally arrives on an open SSE connection; reconnect with `afterSequence` backfills only what's newer.
- `RunReaperTest` — calls `RunReaper.reapExpiredRuns()` directly rather than waiting on its 60s schedule; expires a QUEUED run (FAILED + task BLOCKED); leaves not-yet-expired and WAITING_APPROVAL runs alone.

**Coverage:** Backend: 72 tests across 11 classes (37 new this phase: 17 `RunTransitionValidatorTest`, 13 `RunControllerIntegrationTest`, 2 `EventPublisherIntegrationTest`, 2 `RunEventStreamIntegrationTest`, 3 `RunReaperTest`). Frontend: unchanged (10 tests, 3 files) — Phase 5 has no UI.

**Verification:** `./gradlew build` — BUILD SUCCESSFUL, 72/72 backend tests (Testcontainers `postgres:17` + `rabbitmq:4.1-management` + a plain `redis:7` container). `npm run build` + `npx ng test --watch=false` — unaffected, still 10/10 (Angular client regenerated to prove `openapi.yaml` parses; nothing in `apps/web` calls the new services yet). `docker build` for `apps/api` succeeded. Full live flow against `docker compose up` (with RabbitMQ/Redis health checks re-enabled): login → create project → sync talos.yaml (`agents.preferred: custom-shell`) → create task → start-run with no body (agentKey correctly defaulted) → task auto-moved to RUNNING → internal context/logs/illegal-transition(422)/no-token(401)/legal-transition(200) → RabbitMQ management API confirmed 3 messages on the real `talos.events` exchange → `psql` confirmed every new audit event type including the rejected-transition row surviving its own request's rollback. Compose stack and image removed afterward. `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md` — zero results. Did not re-run the Python services (untouched — Phase 6 is what will actually call these new internal endpoints). CI still hasn't run on GitHub Actions (no remote).

**Notes:**
- `RunEventStreamIntegrationTest` routinely takes the full 30s Spring Boot graceful-shutdown grace period during teardown (server doesn't notice the client's closed socket until the next 15s heartbeat write attempt, which can land after context shutdown has already started). Cosmetic slowdown only, not a failure — noted in case a future session sees the same thing and wonders if something's hung.
- `AgentRunStep`'s "start then complete" semantics (one row per step lifecycle, matched by "most recent open RUNNING row of that stepType") were an inference — Section 10.4's `{stepType, status, summary?}` request shape has no id, so this was the only reading that doesn't produce an orphaned row every time the orchestrator reports progress on the same step.
- Task↔run status mapping (Section 8.2) interpreted `RUNNING_*` as covering every orchestrator-driven intermediate state (including `PREPARING_WORKSPACE`/`REVIEWING`, not just `RUNNING_AGENT`/`RUNNING_TESTS` literally), and folded `APPROVED` into `WAITING_APPROVAL`'s `REVIEW` mapping, so the switch covering all 12 `RunStatus` values is exhaustive with no silently-stale board state. `CANCELLED` restores task status to `READY` unconditionally (no schema column holds "status before this run" — reasoned to be equivalent to `READY` given Phase 4's task-transition matrix makes `READY` the only path into `RUNNING`). Both flagged in the phase report as judgment calls, not silent assumptions.

**Known blockers / follow-ups:**
- `POST /internal/v1/runs/{id}/{changes,artifacts}`, `GET /api/v1/runs/{id}/diff`, `POST /api/v1/runs/{id}/cancel`, `POST /api/v1/runs/{id}/rerun-tests`, `GET /api/v1/projects/{id}/runs` — all still stubbed/deferred; none are in Phase 5's task list and most need Phase 6's runner supervisor to have anything to serve.
- No UI: Phase 5 is API-side only. The board's RUNNING/REVIEW/DONE/BLOCKED columns (Phase 4) will start populating once something calls start-run, but there's no "start run" button in `apps/web` yet.
- 24h WAITING_APPROVAL reminder event (`dev.talos.notifications`) not implemented — out of Phase 5's scope.
- CI still hasn't run on GitHub Actions (no remote configured).

## 2026-07-09 — Phase 4: task board

**Ask:** Implement Section 16 Phase 4: Tasks CRUD + Kanban (API and UI) — server-side transition validation, `board_position` ordering, `move` endpoint, audit on all mutations, Angular `/board` with CDK drag-drop.

**Changed (contracts):**
- `packages/contracts/openapi.yaml` — new `tasks` tag: `GET/POST /tasks`, `GET/PATCH /tasks/{id}`, `POST /tasks/{id}/move`, plus `TaskStatus`/`TaskPriority`/`TaskRiskLevel` enums and `TaskSummary`/`Task`/`TaskDetail`/`CreateTaskRequest`/`PatchTaskRequest`/`MoveTaskRequest`/`PageTaskSummary` schemas, matching Section 10.4's endpoint table verbatim (no `agentKey` on `CreateTaskRequest` — not in the plan's field list for this phase).

**Changed (backend):**
- `dev.talos.tasks.Task` — added `updatePartial()`, `move()`, `@PreUpdate`; flipped `updated_at` to `updatable=true` (same "first phase with a real update path" pattern `Project` went through in Phase 3).
- `dev.talos.tasks.TaskRepository` — added `Page`-returning `findByProjectId`/`findByStatus`/`findByProjectIdAndStatus` for the filtered/paged list endpoint, alongside the pre-existing non-paged `findByProjectIdAndStatus`.
- `dev.talos.tasks.TaskTransitionValidator` (new) — pure function, `Map<TaskStatus, Set<TaskStatus>>` of legal manual targets: `BACKLOG -> {READY, CANCELLED}`, `READY -> {BACKLOG, CANCELLED}`, same-status always legal, everything else (including anything touching `RUNNING`/`REVIEW`/`DONE`/`BLOCKED`) illegal. See the phase report's Deviations section for why those four are reserved for the run state machine (Section 8.2).
- `dev.talos.tasks.dto` (new) — `CreateTaskRequest`, `PatchTaskRequest`, `MoveTaskRequest`, `TaskSummary`, `TaskDetailResponse` (reuses `dev.talos.projects.dto.RunSummary` for the embedded runs list — no duplicate DTO).
- `dev.talos.tasks.TaskService`/`TaskController` — CRUD, `move` (422 `ILLEGAL_TRANSITION` via `ApiException` on an illegal transition), `AuditService.record(...)` on create/update/move (`task.created`/`task.updated`/`task.moved`). First module with mutations that actually write audit rows — Phase 3 left `dev.talos.projects` without this wiring, which Phase 4's own acceptance criteria required fixing here, scoped to `tasks` only.

**Changed (tests):**
- `apps/api/src/test/java/dev/talos/tasks/TaskTransitionValidatorTest.java` (new) — exhaustive `TaskStatus` x `TaskStatus` matrix (49 pairs) via `@ParameterizedTest`/`@EnumSource`, plus the two acceptance-criteria examples spelled out explicitly.
- `apps/api/src/test/java/dev/talos/tasks/TaskControllerIntegrationTest.java` (new) — full CRUD round trip, legal move (persists + audit row), illegal move (422, task status confirmed unchanged), 404. Uses a real `/auth/login` JWT instead of `@WithMockUser`, since task mutations need `@AuthenticationPrincipal AuthenticatedUser` for `requestedBy`/audit actor, and `@WithMockUser`'s `UserDetails` principal can't supply that (would NPE on `principal.id()`).

**Changed (frontend):**
- `npm run generate:api` regenerated (`TasksService` + task models added, existing `AuthService`/`ProjectsService` output unchanged).
- `tasks/task.store.ts` (new) — signal-based store mirroring `ProjectStore`; `load()` pulls one `size=500` page (the board needs full-column context, not pagination — see phase report); `move()` is optimistic (updates the local signal immediately) with rollback to the prior snapshot if the API call rejects.
- `tasks/{task-card,task-column,task-drawer,task-form-dialog}.component.ts` + `board.page.ts` (new) — `/board` route, six columns (Backlog/Ready/Running/Review/Blocked/Done — Cancelled excluded per Section 6.2), `@angular/cdk/drag-drop` (`cdkDropList`/`cdkDrag`) connected across all six columns, `TaskFormDialogComponent` (project picker sourced from `ProjectStore` + title/description/priority/riskLevel, matching `CreateTaskRequest` exactly), `TaskDrawerComponent` (read-only detail + runs list in a `mat-drawer`, opened on card click).
- `app.routes.ts` — `/board` route behind `authGuard`; cross-links added between the Projects and Board toolbars.

**Coverage:** Backend: 35 tests across 6 classes (16 new in `TaskTransitionValidatorTest`, 4 new in `TaskControllerIntegrationTest`). Frontend: 10 tests across 3 spec files (4 new in `board.page.spec.ts` — drop-to-move, no-op same-position drop, rollback-on-rejection snackbar, per-column filter/sort by `boardPosition`).

**Verification:** `./gradlew build` — BUILD SUCCESSFUL, 35/35 backend tests (Testcontainers `postgres:17`). `npm run build` + `npx ng test --watch=false` — clean, 10/10. `docker build` for `apps/api` and `apps/web` both succeeded; `apps/web` image run and curl-smoke-tested (`/`, `/board`, `/assets/env-config.json` all 200). Full live flow against `docker compose up`: login → create project → create task (lands in BACKLOG) → list (filtered by `projectId`+`status`) → get detail (empty `runs`) → patch description → move BACKLOG→READY (200, persists) → move READY→DONE (422 `ILLEGAL_TRANSITION`) → get detail again (status still READY) → `psql` confirmed `task.created`/`task.updated`/`task.moved` audit rows with correct `entity_id` and (for `created`) the seeded admin as `actor_user_id`. Compose stack and both images removed afterward. `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md` — zero results. Did not re-run the Python services (untouched). CI still hasn't run on GitHub Actions (no remote).

**Notes:**
- No browser automation tool available in this session — the CDK drag gesture itself was verified at the component-logic level (`board.page.spec.ts` synthesizing `CdkDragDrop` events), not by observing an actual pointer-drag in a rendered browser. Full HTTP contract (including the 422 path) verified live via `curl`.
- Hit a Gradle-daemon staleness trap: a daemon started earlier in the session (before `sg docker -c` group membership was active) cached a failed Docker-probe result, so `./gradlew test` kept failing with "Could not find a valid Docker environment" even after wrapping subsequent calls in `sg docker -c`. Fixed with `./gradlew --stop` before retrying. Not a code issue — noting it in case a future session hits the same thing.

**Known blockers / follow-ups:**
- `RUNNING`/`REVIEW`/`DONE`/`BLOCKED` board columns render but can't be populated by anything yet — Phase 5 wires up Section 8.2's run-driven task status mapping, which is the only way into those four states by design (see phase report Deviations).
- No "New Task" entry point from `ProjectDetailPage` pre-filled with that project — only the Board's dialog (with a manual project picker) exists; `TaskFormDialogComponent` already supports a pre-filled `projectId` via its dialog data, just not wired to a second launch site.
- `move`'s `boardPosition` only renumbers the dragged task, not its siblings, since Section 10.4 exposes a single-task move endpoint, not a batch reorder — acceptable for the "persists across reload" acceptance criterion, but repeated reordering can eventually produce duplicate `boardPosition` values within a column.
- CI still hasn't run on GitHub Actions (no remote configured).

## 2026-07-09 — Phase 3: project registry

**Ask:** Implement Section 16 Phase 3: Projects CRUD + `talos.yaml` parsing (API and UI), `sync-config` validating against `talos.schema.json` with versioned `project_configs` and single-active enforcement, generated Angular API client, `/projects`/`/projects/:id` routes.

**Changed (contracts):**
- `packages/contracts/openapi.yaml` — `POST /auth/login` and the five Phase 3 project endpoints, tagged `auth`/`projects` (untagged operations all landed in one `DefaultService` on first codegen attempt — regenerated after adding tags). `ProjectDetail` later gained `configHistory` (see below).
- `packages/project-config-schema/talos.schema.json` — JSON Schema (draft 2020-12) for Section 14's `talos.yaml` spec; registered-adapter-key enum excludes `gemini-cli` (Section 7.4 marks it "backlog," not registered).

**Changed (backend):**
- `dev.talos.common.PageResponse<T>` — the Section 10.1 list envelope.
- `dev.talos.projects.TalosConfigParser` — YAML parse (`tools.jackson.dataformat:jackson-dataformat-yaml`) + schema validation (`com.networknt:json-schema-validator:3.0.6`), field-path → message error map on failure.
- `dev.talos.projects`: `ProjectService`/`ProjectController` (CRUD + `sync-config`), DTOs. `Project` gained `update()` + `@PreUpdate` — first entity with a real mutation path, so the "getter-only until the phase that implements mutation" rule from Phase 2's report now applies to the *next* entity that needs it, not `Project`.
- `Project`/`ProjectConfig`/`AgentRun` repositories gained query methods needed for `getDetail()`'s "activeConfig + configHistory + last 5 runs" response (`findByStatus`, `findTopByProjectIdOrderByVersionDesc`, `findByProjectIdOrderByVersionDesc`, `findTop5ByProjectIdOrderByCreatedAtDesc`).
- `apps/api/build.gradle.kts` — added `jackson-dataformat-yaml`, `json-schema-validator`; pulled `talos.schema.json` onto the classpath from its canonical `packages/project-config-schema` location (see bug #1 below for how the first attempt at this broke).
- `apps/api/Dockerfile`, `infra/docker-compose.dev.yml`, `.github/workflows/ci.yml` — `apps/api`'s Docker build context switched to the repo root (same reason as the Maven→Gradle-adjacent `packages/project-config-schema` access need), matching the pattern already used for `apps/orchestrator`/`apps/runner-supervisor`.

**Bug #1 — Gradle resource-filter silently dropped application.yml and both Flyway migrations.** Adding `packages/project-config-schema` as a second `sourceSets.main.resources.srcDir(...)` with `include("talos.schema.json")` next to it filtered *every* resources srcDir (not just the new one) down to that single pattern — `SourceDirectorySet.include()` scopes to the whole source set, not the individual `srcDir()` call it's textually attached to. Surfaced as `relation "users" does not exist` inside `AdminSeeder.run()` during a Testcontainers test — Flyway had run against an empty migration set (`db/migration/*.sql` had vanished from `build/resources/main`), not an obvious "resource not found" error. Fixed with a dedicated `Copy` task copying just `talos.schema.json` into `build/generated-resources/talos-schema`, added as its own `srcDir` with no `include()` filter anywhere near the original `src/main/resources`.

**Bug #2 — every entity's `created_at`/`updated_at` came back `null` in the same-request response, across every entity in the app.** `insertable=false` columns relying on the DB's `DEFAULT now()` are never re-read by Hibernate after `INSERT` unless told to — discovered via manual `curl` against `POST /api/v1/projects`, not by any test (none asserted a non-null timestamp on a same-transaction response). Affected all 12 entities using this pattern since Phase 1/2 (`User`, `AuditEvent`, `Project`, `ProjectConfig`, `Task`, `AgentRun`, `AgentRunLog`, `Approval`, `Integration`, `PullRequest`, `SecretValue`, `IntegrationCredential`), not just `Project`. Fixed with `@Generated(event = EventType.INSERT)` (Hibernate 7's current API — `@GenerationTime`/`GenerationTime.INSERT` from older Hibernate no longer exists) on every affected field, applied via a small Python script across all 11 remaining files after fixing `Project` by hand first. Added `jsonPath("$.createdAt").isNotEmpty()`/`updatedAt` assertions to `ProjectControllerIntegrationTest` so this can't silently regress.

**Changed (frontend):**
- `@openapitools/openapi-generator-cli` (devDependency) + `npm run generate:api` (`typescript-angular` generator) + `openapi-generator-config.json` (`providedInRoot: true`, `useSingleRequestParameter: true`). Generated output committed to git (`apps/web/src/app/api/`) rather than gitignored — keeps `npm ci && npm run build` free of a Java/network dependency; regenerate-and-commit manually whenever `openapi.yaml` changes.
- `core/auth/{auth.store,auth.interceptor,auth.guard}.ts` — JWT held in memory only (a signal, never persisted), `authInterceptor` attaches it, `authGuard` redirects to `/login`. Not assigned to any specific Section 16 phase, but implemented now because every Phase 3 endpoint requires a JWT and there was otherwise no way to demonstrate "create/list/edit a project in the UI" (Phase 3's own acceptance criterion) at all.
- `core/api-config.provider.ts` — reads `/assets/env-config.json` (written by `apps/web/docker-entrypoint.sh` from `TALOS_API_URL`, built in Phase 0 but never consumed until now) via `provideAppInitializer`, falls back to `http://localhost:8080` for `ng serve`/tests where that file doesn't exist.
- `pages/login/`, `projects/{project.store,project-list.page,project-form-dialog.component,project-detail.page,config-panel.component}` — `/projects` and `/projects/:id` routes, `ProjectStore` (signal-based per Section 6.1), Material list/form/detail/config UI. `ConfigPanelComponent` shows `error.details` field errors inline on a failed sync and a version-history expansion panel (see contract deviation below).

**Coverage:** Backend: 15 tests across 4 classes (`AuthControllerIntegrationTest` 4, `CoreSchemaRepositoryTest` 4, `TalosConfigParserTest` 3, `ProjectControllerIntegrationTest` 4 — now including the timestamp regression assertions). Frontend: 6 tests across 2 spec files (`app.spec.ts` 2, `project-form-dialog.component.spec.ts` 4).

**Verification:** `./gradlew build` — BUILD SUCCESSFUL, 15/15 backend tests. `npm run build` + `npx ng test --watch=false` — clean, 6/6. `docker build` for `apps/api` (repo-root context) and `apps/web` both succeeded; `apps/web` image run and curl-smoke-tested (`/` and `/login` SPA-fallback route both 200). Full live flow against `docker compose up`: login → create (non-null timestamps confirmed) → list → get detail (empty config/runs) → update (`updatedAt` confirmed to advance past `createdAt`) → sync-config valid (Section 14's example YAML, version 1) → sync-config invalid (422, `error.details` names the missing fields) → get detail again (`activeConfig`/`configHistory` populated). Compose stack and images removed afterward. `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md` — zero results. Did not re-run the Python services (untouched). CI still hasn't run on GitHub Actions (no remote).

**Notes:**
- No browser automation tool is available in this session. "Create/list/edit a project in the UI" was verified by driving the same HTTP calls the generated Angular client makes, plus a clean build/test and a curl-smoke-tested running container — not by observing the rendered UI in an actual browser. Said so explicitly rather than implying full UI verification happened.
- `GET /projects/{id}`'s `configHistory` field is an enrichment of an existing endpoint, not a new one — Section 10 forbids inventing endpoints, but Phase 3's acceptance criteria require version history to be visible, and there was no other way to satisfy both constraints at once. Reasoning kept here and in the phase report rather than assumed self-evident from the diff.

**Known blockers / follow-ups:**
- `ProjectDetailPage` doesn't yet have an edit-project entry point reusing `ProjectFormDialogComponent` (Section 15 implies the same dialog serves create and edit) — `PUT /projects/{id}` is fully implemented/tested API-side, just not wired to a second UI trigger yet.
- `ProjectListPage` doesn't expose the `status` filter or pagination controls yet — both already work API-side.
- CI still hasn't run on GitHub Actions (no remote configured).

## 2026-07-09 — Phase 2: database and migrations

**Ask:** Implement Section 16 Phase 2: the full MVP schema from Section 9.2 (`V002__core_schema.sql`) and JPA entities/repositories for every remaining table, nothing from Section 9.4.

**Changed (backend):**
- `db/migration/V002__core_schema.sql` — `projects`, `project_configs`, `tasks`, `agent_runs`, `agent_run_steps`, `agent_run_logs`, `approvals`, `git_changes`, `pull_requests`, `integrations`, `secret_values`, `integration_credentials`, copied verbatim from Section 9.2, in FK-safe order.
- `dev.talos.projects`: `ProjectStatus` enum, `Project`/`ProjectConfig` entities + repositories. `ProjectConfig.setActive()` is the only setter added anywhere this phase — needed to enforce "at most one `is_active=true` row per project," the one mutation Section 9.2 names explicitly.
- `dev.talos.tasks`: `TaskStatus`/`TaskPriority`/`TaskRiskLevel` enums, `Task` entity + repository.
- `dev.talos.runs`: `RunStatus`/`TestStatus`/`ReviewStatus`/`StepType`/`StepStatus`/`LogStream`/`GitChangeType` enums, `AgentRun`/`AgentRunStep`/`AgentRunLog`/`GitChange` entities + repositories.
- `dev.talos.approvals`: `ApprovalStatus` enum, `Approval` entity + repository.
- `dev.talos.integrations`: `PullRequestStatus` enum, `Integration`/`PullRequest` entities + repositories. Neither table is explicitly assigned a module in Section 6.2; placed here since `Integration` is self-evidently this module's and `PullRequest` belongs to the GitHub push/PR pipeline Phase 9 assigns to `dev.talos.integrations` — see the phase report for this as a flagged judgment call, not a literal instruction.
- `dev.talos.secrets`: `SecretValue`/`IntegrationCredential` entities + repositories, both javadoc'd as never-exposed-via-REST per Section 12.2.
- Every unconstrained `VARCHAR` column that the DDL only comments example values for (`tasks.source`, `agent_runs.provider_auth_mode`, `approvals.approval_type`, `integrations.type`, `integration_credentials.auth_mode`) was left as a plain `String` field — no CHECK constraint in the DDL means no invented Java enum either.

**Root cause (bug fixed this phase):** `ddl-auto=validate` failed on first run: `wrong column type encountered in column [message] in table [agent_run_logs]; found [text (Types#VARCHAR)], but expecting [oid (Types#CLOB)]`. Every `TEXT`/`BYTEA` column had been mapped with `@Lob`, which on PostgreSQL makes Hibernate target `Types.CLOB`/`Types.BLOB` — implemented via Postgres's `oid` large-object mechanism, not the plain `text`/`bytea` types the migration declares. Removed `@Lob` from all seven affected fields (`AgentRun.prompt`/`summary`/`errorMessage`, `Approval.notes`, `ProjectConfig.configYaml`, `Task.description`, `AgentRunStep.summary`) plus `SecretValue`'s two `byte[]` fields; Hibernate's un-annotated default mapping for `String`/`byte[]` is exactly the `VARCHAR`-family/`VARBINARY`-family type Postgres's `TEXT`/`BYTEA` actually are.

**Changed (tests):**
- `apps/api/src/test/java/dev/talos/CoreSchemaRepositoryTest.java` (new) — one round-trip test per new entity, built as a real FK chain (project → task → run → step/log/change/approval/PR; integration → secret → credential) since orphan UUIDs would be rejected by the FK constraints anyway, so this doubles as a referential-integrity check. Plus `flywayMigrateTwice_isIdempotent`, which re-invokes the autowired `Flyway` bean's `.migrate()` inside a running context and asserts zero new migrations execute.

**Coverage:** `CoreSchemaRepositoryTest` (4 tests) + Phase 1's `AuthControllerIntegrationTest` (4 tests) = 8 tests, all green. Every one of the 12 new tables has at least one entity persisted and re-read in the test suite.

**Verification:** `./gradlew build` — BUILD SUCCESSFUL, 8/8 tests. `docker compose -f infra/docker-compose.dev.yml up -d --build` — `talos-api` healthy; `psql` confirmed all 15 tables (14 domain + `flyway_schema_history`) exist and both `V001`/`V002` rows show `success=t`; re-ran the Phase 1 login smoke test to confirm no regression. Compose stack and built image torn down afterward. `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md` — zero results. Did not re-run `apps/web`/Python suites (untouched this phase). CI still hasn't run on GitHub Actions (no remote) — unchanged caveat.

**Known blockers / follow-ups:**
- Every entity's `created_at`/`updated_at` is still `insertable=false, updatable=false` (DB-default only) — correct for now since nothing mutates rows yet, but each phase that adds a real update path (Phase 3 onward) needs to flip `updated_at` to `updatable=true` and set it explicitly to actually satisfy Section 9.1's "maintained by the API on every mutation" rule. Flagged in the phase report so it isn't silently forgotten.
- CI still hasn't run on GitHub Actions (no remote configured).

## 2026-07-09 — Phase 1: backend foundation (auth, errors, audit)

**Ask:** Implement Section 16 Phase 1: a running API with login, JWT security, the standard error envelope, and an audit writer — `dev.talos.auth`, `dev.talos.audit`, `dev.talos.common`, `V001__users_and_audit.sql`.

**Changed (backend):**
- `db/migration/V001__users_and_audit.sql` — `users`/`audit_events` DDL, copied verbatim from Section 9.2.
- `dev.talos.common`: `UuidV7` (wraps `com.github.f4b6a3:uuid-creator`'s `getTimeOrderedEpoch()`), `ErrorResponse`/`ApiException`/`GlobalExceptionHandler` (the Section 10.1 envelope), `TalosProperties` (`@ConfigurationProperties(prefix="talos")` binding the `TALOS_*` env vars from Appendix A).
- `dev.talos.audit`: `AuditEvent` entity (`details_json` mapped as `Map<String,Object>` via `@JdbcTypeCode(SqlTypes.JSON)`, Hibernate's native JSON support — no extra library needed), `AuditEventRepository`, `AuditService.record(...)`.
- `dev.talos.auth`: `Role` enum, `User` entity (id/createdAt/updatedAt all `insertable=false,updatable=false`, left to the DB's `DEFAULT now()` — nothing mutates a `User` after creation yet, so there's no premature `@PreUpdate`/touch() machinery), `UserRepository`, `AdminSeeder` (idempotent `ApplicationRunner`, see phase report for why this isn't a SQL migration), `JwtService` (`io.jsonwebtoken:jjwt` 0.13.0 — not covered by any Boot starter, added explicitly), `AuthenticatedUser` principal, `JwtAuthenticationFilter`, `JsonAuthenticationEntryPoint`, `SecurityConfig` (stateless, `/actuator/health` + `POST /auth/login` public, everything else authenticated), `AuthService`/`AuthController`/`dto.LoginRequest`/`dto.LoginResponse` for `POST /api/v1/auth/login`.
- `apps/api/build.gradle.kts` — added `uuid-creator`, `jjwt-{api,impl,jackson}`, `spring-boot-testcontainers` + pinned `testcontainers-{junit-jupiter,postgresql}:2.0.5`; disabled the Spring Boot plugin's plain `jar` task output was already off from the Maven→Gradle switch, unrelated here. Added `tasks.withType<JavaCompile> { options.compilerArgs.add("-Xlint:deprecation") }` permanently after it caught a real deprecated-API usage (see Notes below and the phase report).
- `apps/api/src/main/resources/application.yml` — disabled `management.health.redis`/`management.health.rabbit` (neither integration is load-bearing until Phase 5+; leaving them on made `/actuator/health` falsely flaky).
- `apps/api/src/main/java/dev/talos/common/GlobalExceptionHandler.java` — added a specific `NoResourceFoundException -> 404` handler. Without it, the catch-all `Exception` handler turned every unmatched route into a 500. Found via manual `curl` smoke testing against the live compose stack, not by the unit tests (see phase report Notes for why).
- `infra/docker-compose.dev.yml` — added the `api` service (build context `../apps/api`, **not** repo root — unlike `orchestrator`/`runner-supervisor`, `apps/api` has no shared-package dependency, so it builds from its own directory), wired to `postgres`/`rabbitmq`/`redis` with `depends_on: condition: service_healthy` and dev-only placeholder secrets (same "committed on purpose, obviously fake" pattern as the Phase 0 Postgres credentials).

**Changed (tests):**
- `apps/api/src/test/java/dev/talos/auth/AuthControllerIntegrationTest.java` — `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers `postgres:17` via `@ServiceConnection`. Four cases: seeded login succeeds and writes the audit row, wrong password → 401, unauthenticated request to an arbitrary path → 401, `/actuator/health` → 200. RabbitMQ/Redis aren't containerized for this test (not used yet); pointed their config at `localhost` addresses that are never dialed during the test's requests, rather than standing up containers for integrations nothing exercises.

**Coverage:** `AuthControllerIntegrationTest` (4 tests) is currently the only test class in `apps/api`; it exercises the full login round-trip end to end against a real Postgres, not mocks.

**Verification:** `./gradlew build` — BUILD SUCCESSFUL (compile + all 4 tests, via Testcontainers). Manual `curl` pass against `docker compose -f infra/docker-compose.dev.yml up -d --build`: health public (200), correct login (200 + JWT), wrong password (401 `INVALID_CREDENTIALS`), no token (401 `UNAUTHORIZED`), valid token on an unmatched route (404 `NOT_FOUND`, confirming the `NoResourceFoundException` fix). Confirmed the audit row directly via `psql`. Compose stack and the built image torn down afterward. `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md` — zero results. Did not re-run `apps/web`/Python test suites (untouched this phase). CI has not run on GitHub Actions yet (no remote configured) — same standing caveat as Phase 0.

**Notes:**
- Three separate "the obvious API from Spring Boot 3 / Testcontainers 1.x has moved" surprises this phase, all from being on genuinely new major versions (Boot 4.1, Jackson 3, Testcontainers 2.0, Spring Framework 7): Jackson's `ObjectMapper` moved to `tools.jackson.databind` (new Maven groupId `tools.jackson`, not `com.fasterxml.jackson`); `org.springframework.lang.NonNull` is deprecated in favor of JSpecify's `org.jspecify.annotations.NonNull`; Testcontainers' `junit-jupiter`/`postgresql` artifacts were renamed to `testcontainers-junit-jupiter`/`testcontainers-postgresql` and `PostgreSQLContainer` moved packages and dropped its generic self-type. None of these are called out in the implementation plan (reasonably — it predates verifying exact library APIs), so each was resolved by inspecting the actual resolved classpath/jar contents rather than guessing, per the plan's "verify against observed reality" instruction for exactly this kind of drift.
- `grep -ri agentos .`'s scoping needed to grow again: `CLAUDE.md`/`AGENTS.md` (added between Phase 0 and Phase 1) document the naming rule itself, so the unscoped command self-matched them the same way it already self-matched `docs/`. Extended CI's `naming-guard` job and both files' own text to also `--exclude=CLAUDE.md --exclude=AGENTS.md`. Phase 0's report/log entries are left untouched (accurate when written); see the Phase 1 report's Deviations section for why that's not a rewrite of history.

**Known blockers / follow-ups:**
- CI still hasn't run on GitHub Actions (no remote). Docker is available by default on GitHub's `ubuntu-latest` runners, so `AuthControllerIntegrationTest`'s Testcontainers usage is expected to work there unmodified, but that's unconfirmed.
- `apps/api`'s `.dockerignore`/`.gitignore`/`.gitattributes` and `application.yml` datasource block are all still Phase 0/1-era; nothing about them is expected to change until Phase 2 adds the rest of the schema.

## 2026-07-09 — apps/api build tool: Maven → Gradle

**Ask:** Before starting Phase 1, switch `apps/api` from Maven to Gradle. Operator chose Kotlin DSL over Groovy when asked.

**Root cause / context:** Not a bug fix — an explicit operator-directed deviation from Section 4.0 of the implementation plan, which pins Maven. Everything else pinned in that section (Spring Boot 4.1.0, Java 21, package `dev.talos`, exact starter set) is unchanged.

**Changed (backend):**
- Regenerated the project via start.spring.io with `type=gradle-project-kotlin` instead of `maven-project`, same dependency list as the original Phase 0 scaffold. Hit the same `bootVersion` metadata bug as Phase 0 (`4.1.0.RELEASE` doesn't resolve — the Gradle codepath fails server-side at generation time rather than deferring to a later build failure like Maven's did); used `4.1.0`.
- `apps/api/build.gradle.kts` (new), `settings.gradle.kts` (new), `gradle/wrapper/*`, `gradlew`/`gradlew.bat` (new) — replace `pom.xml`, `mvnw`/`mvnw.cmd`, `.mvn/`.
- `build.gradle.kts` — added `tasks.named<Jar>("jar") { enabled = false }`. The Spring Boot Gradle plugin's default `jar` task emits a `-plain.jar` alongside the executable `bootJar` output; left enabled, the Dockerfile's `COPY .../talos-api-*.jar app.jar` wildcard matched both files and failed. Disabling the plain jar (we only ever ship the executable one) is the standard fix.
- `apps/api/Dockerfile` — build stage now copies `gradle/`, `gradlew`, `build.gradle.kts`, `settings.gradle.kts`, runs `./gradlew dependencies` then `./gradlew bootJar -x test`; runtime stage copies from `build/libs/` instead of `target/`.
- `apps/api/.dockerignore`, `.gitignore`, `.gitattributes` — regenerated for Gradle (`.gradle/`, `build/`, wrapper jar exception) in place of the Maven versions.
- Root `.gitignore` — "Java / Maven" section replaced with "Java / Gradle" (`.gradle/`, wrapper-jar exception path updated).
- `src/main/java/dev/talos/TalosApiApplication.java` and `src/main/resources/application.yml` (Phase 0's trimmed config, no `contextLoads` test) carried over untouched — this was a build-tool swap, not a source change.
- `.github/workflows/ci.yml` — `api` job now uses `gradle/actions/setup-gradle@v4` and `./gradlew --no-daemon build` instead of `./mvnw -B verify`.
- `README.md`, `docs/architecture.md`, `docs/phase-reports/phase-0-report.md` — updated to describe Gradle instead of Maven; `docs/architecture.md` and the phase-0 report both record this as an explicit approved deviation from Section 4.0, distinct from the earlier mechanical/tooling corrections.

**Verification:** `./gradlew build` (after removing stale `build/`) — BUILD SUCCESSFUL, empty test suite (`NO-SOURCE` on `:test`), matching Phase 0's Maven baseline. `docker build -f apps/api/Dockerfile apps/api` — succeeded end to end (dependency resolution → `bootJar` → runtime image), confirming the `-plain.jar` fix. Did not re-run `apps/web` or the Python projects' checks since this change is scoped entirely to `apps/api`.

**Known blockers / follow-ups:**
- CI still hasn't run on GitHub Actions (no remote configured); the updated `api` job is unexercised there, same caveat as Phase 0.

## 2026-07-09 — Phase 0: repository and tooling setup

**Ask:** Bootstrap the Talos monorepo per Section 16 Phase 0 of the implementation plan: the full directory tree from Section 5 (minus Phase 12 remote-trigger adapters), dev infra compose, contract scaffolding, `.env.example` per app, pinned-version docs, and a working build for every app.

**Changed (repo root):**
- `.gitattributes` — `text=auto eol=lf` with explicit LF for `*.sh`/`mvnw`; the host's global `core.autocrlf=true` would otherwise corrupt shell scripts and Dockerfiles committed from this WSL environment. Set `core.autocrlf=false` locally on this repo as well.
- `.gitignore`, `.dockerignore` — standard excludes; root `.dockerignore` matters because `apps/orchestrator` and `apps/runner-supervisor` build with the repo root as Docker context (see below).
- `README.md` — monorepo layout, service table, local dev commands.

**Changed (apps/web):** Angular 22 scaffolded via `ng new` (standalone components, signals, Vitest, SCSS, no SSR) plus `ng add @angular/material` for Material + CDK. Node 22.23.1 installed via `nvm` and pinned in `apps/web/.nvmrc` — the host's default Node (24.11.1, from linuxbrew, ahead of nvm on `PATH`) is below Angular 22's minimum. Replaced the generated splash template (`app.html`) with a bare `<router-outlet />` since routes arrive in later phases, and updated `app.spec.ts` accordingly. Added `Dockerfile` (multi-stage, nginx:1.27-alpine) and `docker-entrypoint.sh`, which renders `/assets/env-config.json` from `TALOS_API_URL` at container start per Appendix A ("injected at container start into the served config").

**Changed (apps/api):** Spring Boot 4.1.0 scaffolded via start.spring.io (package `dev.talos`, Maven, Java 21; web/data-jpa/security/validation/postgresql/flyway/actuator/amqp/data-redis/configuration-processor starters). start.spring.io's `bootVersion` id (`4.1.0.RELEASE`) doesn't match the actual Maven Central artifact version (`4.1.0`, no `.RELEASE` suffix for Boot 4.x) — corrected in `pom.xml`. Removed the generated `contextLoads` test: it boots the full context including a live Postgres/Flyway connection, which doesn't exist yet (no entities, no compose-started DB in CI at this phase); Phase 0's acceptance criteria explicitly allow empty test suites, and a real `contextLoads` test returns in Phase 1/2 backed by Testcontainers. Replaced `application.properties` with `application.yml` wiring the Appendix A env vars (`TALOS_DB_URL`, `TALOS_RABBITMQ_URL`, `TALOS_REDIS_URL`, `TALOS_JWT_SECRET`, etc.) with local defaults. Added `Dockerfile` (multi-stage, eclipse-temurin 21).

**Changed (packages/agent-adapter-spec, apps/orchestrator, apps/runner-supervisor):** Three `uv`-managed Python 3.12 projects (`uv init --lib` / `--app --package`). `agent-adapter-spec` is consumed by both apps as an editable path dependency (`tool.uv.sources`), matching Section 6.3's "shares `packages/agent-adapter-spec` with the orchestrator." Pinned dependencies: `aio-pika`/`httpx` (orchestrator), `fastapi`/`uvicorn[standard]`/`httpx` (runner-supervisor), `pytest`/`pytest-asyncio` (dev, both). Orchestrator's module layout (`main.py`, `pipeline.py`, `api_client.py`, `runner_client.py`, `adapters.py`, `locks.py`) follows Section 6.3's explicit file list exactly; runner-supervisor has no such prescribed layout in the plan, so it was left as a single bootstrap stub rather than inventing submodule names ahead of Phase 6. Every stub function raises `NotImplementedError` naming the phase that fills it in, rather than silently doing nothing. Each project has one real (not fake) test — a package-import assertion — because an empty pytest suite exits 5 (failure), not 0. Added `Dockerfile` for each app; both use the **repo root** as build context (`docker build -f apps/orchestrator/Dockerfile .`) so the shared `agent-adapter-spec` source can be copied in alongside the app.

**Changed (packages/contracts, packages/project-config-schema):** `openapi.yaml` stub (OpenAPI 3.1, empty `paths`, error-envelope schema from Section 10.1) plus `events/README.md` describing the event envelope from Section 11. No endpoints or event schemas defined yet — those are added in the phase that implements their producer/consumer, not speculatively now. `project-config-schema/README.md` placeholder; `talos.schema.json` itself is Phase 3 work.

**Changed (infra):** `docker-compose.dev.yml` — `postgres:17`, `rabbitmq:4.1-management`, `redis:7`, each with a healthcheck and a named volume. Host-side ports for Postgres (5433) and RabbitMQ (5673/15673) are shifted from their defaults because this dev machine already runs unrelated native services on 5432/5672/15672; container-internal ports are untouched, so `TALOS_*_URL` values used between containers on the Docker network stay standard. `infra/dokploy/README.md` and `workers/*/README.md` placeholders for Phase 10/11 content — directories exist per the Section 5 tree, but no Dockerfiles are written yet since there's nothing to build.

**Coverage:** No application logic exists yet, so "coverage" here means each app's toolchain is provably wired up: `mvnw verify`, `ng build` + `ng test`, and `uv run pytest` (x3) all exit 0, and all four Dockerfiles build successfully.

**Verification:**
- `apps/web`: `npm run build` — success (222.76 kB initial, 2.95s). `ng test --watch=false` (Vitest) — 2/2 passed.
- `apps/api`: `./mvnw verify` — BUILD SUCCESS, empty test suite (0 tests), jar produced at `target/talos-api-0.0.1-SNAPSHOT.jar`.
- `packages/agent-adapter-spec`, `apps/orchestrator`, `apps/runner-supervisor`: `uv run pytest` — 1/1 passed each.
- `docker build` for all four apps (`talos-web`, `talos-api`, `talos-orchestrator`, `talos-runner-supervisor`) — all succeeded; `talos-web` additionally smoke-tested by running the container and curling `/` (200) and `/assets/env-config.json` (200, correct `TALOS_API_URL` substitution).
- `docker compose -f infra/docker-compose.dev.yml up -d` — all three services reached `healthy` within ~5s; torn down after verification (`docker compose down`).
- `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github` — zero results. (The unscoped command self-matches this rule's own text in `docs/` and the CI step name; CI's `naming-guard` job excludes `docs/` and `.github/` for that reason.)
- CI workflow (`.github/workflows/ci.yml`) written to run all of the above on every PR, but not yet exercised by an actual GitHub Actions run (no remote configured for this repo yet).

**Notes:** Docker and `uv` were not present in the initial shell session (`which docker`/`which uv` failed); the operator confirmed both were installed and accessible via `sg docker -c '<cmd>'` (group membership not yet active in existing shells) before scaffolding began. Git is configured locally (not globally) with `user.name = Paul Bernard`, `user.email = paulvbernard73@gmail.com`, per operator instruction to commit after each phase goal without co-authoring trailers.

**Known blockers / follow-ups:**
- CI has not actually run in GitHub Actions (repo has no remote yet); the workflow is written and locally-equivalent commands were verified manually.
- `apps/api` has no entities, migrations, or real config validation yet — `application.yml`'s datasource block is a Phase 1/2 placeholder that will need revisiting once Flyway migrations exist.
- `packages/contracts/runner-api.yaml` (Section 10.5) does not exist yet; deferred to Phase 6 when the runner supervisor's HTTP contract is implemented.
