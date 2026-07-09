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
