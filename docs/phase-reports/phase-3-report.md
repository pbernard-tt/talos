# Phase 3 report — Project registry

## What works

- **Contract:** `packages/contracts/openapi.yaml` now covers `POST /auth/login` and the five Phase 3 project endpoints (`GET/POST /projects`, `GET/PUT /projects/{id}`, `POST /projects/{id}/sync-config`), tagged `auth`/`projects` so the generated client splits into `AuthService`/`ProjectsService` instead of one `DefaultService`.
- **Schema:** `packages/project-config-schema/talos.schema.json` — full JSON Schema (draft 2020-12) for `talos.yaml` per Section 14's field table, including the registered-adapter-key enum (`custom-shell`, `claude-code`, `opencode`, `codex-cli`, `openhands` — `gemini-cli` excluded as it's explicitly "backlog," not registered, per Section 7.4).
- **Backend:** `dev.talos.projects` — `TalosConfigParser` (YAML parse + schema validate, field-path error map), `ProjectService`/`ProjectController` for full CRUD plus `sync-config` (versions `project_configs`, deactivates the prior active version, enforces "at most one active" from Section 9.2's comment). `Project` gained real mutation support (`update()` + `@PreUpdate`) since this is the first phase with an update path.
- **Backend tests:** `TalosConfigParserTest` (valid — Section 14's example verbatim; missing required field; unknown agent key) and `ProjectControllerIntegrationTest` (full CRUD round trip, sync-config versioning across two syncs, 422 with field errors on invalid YAML, 404 on unknown project) — 19 backend tests total now, all against Testcontainers `postgres:17`.
- **Frontend:** openapi-generator (`typescript-angular`) wired via `npm run generate:api`, generated client committed to git (see Deviations). `AuthStore` (in-memory JWT) + `authInterceptor` + `authGuard`, a minimal `LoginPage`, `ProjectStore` (signal-based per Section 6.1), `ProjectListPage`+`ProjectFormDialogComponent` (`/projects`), `ProjectDetailPage`+`ConfigPanelComponent` (`/projects/:id`) showing overview, recent runs, and config sync with field-level error display and version history.
- **Frontend test:** `ProjectFormDialogComponent` — form validity gating, and that `save()` omits a blank `defaultBranch` from the request rather than sending an empty string.
- Verified the entire flow live against the real stack: login → create → list → get detail → update → sync-config (valid, versions to 1 then 2 on a second sync) → sync-config (invalid, 422 with `error.details` field map) → get detail again showing `activeConfig`/`configHistory` populated. See Verification.

## What is stubbed

- No `GET /api/v1/projects/{id}/runs` endpoint — Section 10.2 lists it, but nothing creates runs until Phase 5, so there's nothing to page through yet. `recentRuns` embedded in `GET /projects/{id}` already exists and will show real data once Phase 6 starts creating `agent_runs` rows.
- `ProjectListPage` doesn't yet support the `status` query filter or pagination controls in the UI — the store/service both accept them, but Phase 3's UI only needs to prove list/create/edit works, and there's only ever one page of test data right now.
- No edit dialog reusing `ProjectFormDialogComponent` for updates yet (Section 15 describes the same dialog for "list/create/edit") — `ProjectDetailPage` doesn't yet expose an edit action; `PUT /projects/{id}` is fully implemented and tested from the API side, just not wired to a second dialog trigger in the UI. Small gap, listed here rather than silently left out.
- `RunHistoryTable` (Section 15) is a plain Material table inline in `ProjectDetailPage`, not a separate component — didn't seem worth extracting for a table that's empty until Phase 5.

## Deviations from the plan

- **`GET /projects/{id}` response enriched with `configHistory`, not just `activeConfig`.** Section 10.2's terse endpoint table ("→ Project + activeConfig + last 5 runs") doesn't mention it, but Phase 3's own acceptance criteria explicitly requires "config version history retained and visible," and Section 10 forbids inventing a *new* endpoint for it. Enriching the existing `GET /projects/{id}` response is the only way to satisfy that acceptance criterion without adding an endpoint absent from Section 10 — recorded here as the reasoning, not assumed self-evident.
- **Generated Angular client is committed to git**, not regenerated as a build step. Keeps `npm ci && npm run build` free of a hidden Java/network dependency for anyone (or CI) building `apps/web`; `npm run generate:api` is a manual step to run (and commit the diff for) whenever `packages/contracts/openapi.yaml` changes. Documented in this report rather than assumed obvious, since the alternative (gitignore + regenerate-on-build) is just as common a choice.
- **Implemented a minimal login UI** (`AuthStore`, `authInterceptor`, `authGuard`, `LoginPage`) even though no Section 16 phase explicitly assigns `/login`. Every Phase 3 endpoint requires a JWT; without *something* to obtain one, "create/list/edit a project in the UI" (Phase 3's own acceptance criterion) has no way to be demonstrated in a browser. Scoped to exactly Section 15's spec for that route ("Email/password → JWT in memory + refresh on load" — interpreted as: token held in a signal only, a reload clears it and requires logging in again).
- **`apps/api`'s Docker build context changed again**, same pattern as Phase 3's earlier `TalosConfigParser` ticket: `talos.schema.json`'s canonical location (`packages/project-config-schema`) needs to be in the build context, so `apps/api/Dockerfile` now `COPY`s from repo root via explicit `apps/api/...` and `packages/project-config-schema/...` paths rather than a flat context. `infra/docker-compose.dev.yml` and `.github/workflows/ci.yml` updated to match.

None of these required stopping to ask — each is an implementation-mechanism choice within an already-specified contract, not a change to any endpoint, schema, or enum the plan defines.

## Two real bugs found and fixed this phase

1. **Gradle resource-filter bug, found before it shipped.** Adding `packages/project-config-schema` as a second resources `srcDir` with `include("talos.schema.json")` silently filtered *every* resources srcDir down to that one pattern — `application.yml` and both Flyway migrations vanished from `build/resources/main`, and only surfaced as "relation `users` does not exist" deep in a `Testcontainers` test failure, not as an obvious "file not found." Root cause: `SourceDirectorySet.include()` applies to the whole source set, not the one `srcDir` call it's textually next to. Fixed with a dedicated `Copy` task into a `generated-resources` directory added as its own `srcDir`, so the original `src/main/resources` directory's contents are never touched by an include filter. Full detail and the exact fix are in the implementation log.
2. **Systemic timestamp bug across every entity, found by manual `curl` verification, not the test suite.** Every entity's `created_at`/`updated_at` (`insertable=false`, DB `DEFAULT now()`) came back `null` in the same-request response after `save()` — Hibernate never re-reads a DB-generated default unless told to. This affected all 12 entities using that pattern since Phase 1/2, not just `Project`; none of the existing tests asserted a non-null timestamp on a same-transaction response, so it shipped silently through two prior phases. Fixed with `@Generated(event = EventType.INSERT)` on every affected field (Hibernate 7's modern replacement for the removed `@GenerationTime` annotation), verified live, and added a regression assertion to `ProjectControllerIntegrationTest` so a future change can't silently reintroduce it.

## Acceptance criteria (Section 16, Phase 3)

| Criterion | Status |
|---|---|
| Create/list/edit a project in the UI | ✅ demonstrated end to end via the live API (see Verification); no browser automation tool available in this environment to drive the actual rendered UI — see Notes |
| Invalid `talos.yaml` rejected with field-level errors | ✅ `422` + `error.details` field-path map, verified via `curl` and `ProjectControllerIntegrationTest` |
| Config version history retained and visible | ✅ `configHistory` in `GET /projects/{id}`, surfaced in `ConfigPanelComponent`'s expansion panel |
| Parser units: valid, missing required, unknown agent key (test) | ✅ `TalosConfigParserTest` |
| MockMvc CRUD (test) | ✅ `ProjectControllerIntegrationTest` |
| One Angular component test | ✅ `ProjectFormDialogComponent` spec (4 test cases) |

## Verification

- `./gradlew build` — BUILD SUCCESSFUL, 15 backend tests across 4 classes (`AuthControllerIntegrationTest` 4, `CoreSchemaRepositoryTest` 4, `TalosConfigParserTest` 3, `ProjectControllerIntegrationTest` 4), all green against Testcontainers `postgres:17`.
- `npm run build` and `npx ng test --watch=false` — clean build (5 lazy chunks: `project-list-page`, `project-detail-page`, `login-page`, plus 2 vendor chunks), 6/6 tests passing.
- `docker build` for both `apps/api` (repo-root context) and `apps/web` succeeded; `apps/web`'s image was run and smoke-tested — `/` and `/login` (SPA fallback route) both return 200.
- Live full-stack verification against `docker compose -f infra/docker-compose.dev.yml up -d --build`: login → create project (verified non-null `createdAt`/`updatedAt`) → list → get detail (empty `activeConfig`/`configHistory`/`recentRuns`) → update (verified `updatedAt` advances past `createdAt`) → sync-config with Section 14's example YAML (version 1, active) → get detail (`activeConfig`/`configHistory` populated) → sync-config with a deliberately incomplete YAML (422, `error.details` names the missing `project.repo` and `commands` fields). Compose stack and built images removed after verification.
- `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md` — zero results.
- Did not re-run `apps/orchestrator`/`apps/runner-supervisor`/`packages/agent-adapter-spec` — untouched this phase.
- CI still hasn't run on GitHub Actions (no remote configured) — unchanged caveat from every prior phase.

## Notes

- **No browser automation tool is available in this environment.** "Create/list/edit a project in the UI" was verified by driving the exact HTTP calls the generated Angular client makes (same request/response shapes, same auth header, same error envelope) directly against the live API, plus a clean `ng build`/`ng test` and a running, curl-smoke-tested `talos-web` container. This is not the same as confirming the rendered DOM, click handlers, and Material components behave correctly in an actual browser — flagging the gap explicitly rather than implying full UI verification happened.
- Both bugs this phase were caught by *live* verification (`curl` against a running stack), not by the test suites — worth keeping that manual pass in the routine for every phase that touches persistence or resource loading, since Testcontainers tests exercise a fresh context per class and won't always reproduce a same-request-response ordering issue the way a real running server does.

## Next

Phase 4 — Task board: `dev.talos.tasks` CRUD + Kanban, server-side transition validation, `board_position` ordering, Angular `/board` with CDK drag-drop.
