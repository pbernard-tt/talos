# Phase 2 report — Database and migrations

## What works

- `V002__core_schema.sql` — every remaining Section 9.2 table, copied verbatim: `projects`, `project_configs`, `tasks`, `agent_runs`, `agent_run_steps`, `agent_run_logs`, `approvals`, `git_changes`, `pull_requests`, `integrations`, `secret_values`, `integration_credentials`. Nothing from Section 9.4 (`project_environments`, `memory_documents`, `memory_chunks`) was created.
- A JPA entity + Spring Data repository for every one of those 12 tables, one CHECK-constrained enum class per constrained column (`ProjectStatus`, `TaskStatus`/`TaskPriority`/`TaskRiskLevel`, `RunStatus`/`TestStatus`/`ReviewStatus`/`StepType`/`StepStatus`/`LogStream`/`GitChangeType`, `ApprovalStatus`, `PullRequestStatus`) matching Section 9.3 and the DDL's `CHECK (... IN (...))` lists exactly. Columns the DDL leaves unconstrained (`tasks.source`, `agent_runs.provider_auth_mode`, `approvals.approval_type`, `integrations.type`, `integration_credentials.auth_mode`) were deliberately left as plain `String` fields rather than invented enums, since the plan itself only comments example values for these ("DASHBOARD|WEBHOOK|TELEGRAM|...", "'RUN_RESULT','DEPLOY',...") without a CHECK constraint.
- Entities live in the module Section 6.2 assigns them to: `dev.talos.projects` (`Project`, `ProjectConfig`), `dev.talos.tasks` (`Task`), `dev.talos.runs` (`AgentRun`, `AgentRunStep`, `AgentRunLog`, `GitChange`), `dev.talos.approvals` (`Approval`), `dev.talos.integrations` (`Integration`, `PullRequest`), `dev.talos.secrets` (`SecretValue`, `IntegrationCredential`). `GitChange` and `PullRequest` aren't explicitly assigned a module by Section 6.2 (only their DDL and FK shape are specified) — placed with `agent_runs` and `integrations` respectively based on which module's stated responsibility they most directly serve (run diff output; the GitHub push/PR pipeline named in Phase 9's file list). Flagging this as a judgment call, not a literal instruction.
- `JdbcTypeCode(SqlTypes.JSON)` (Hibernate's native JSON support, no extra library) maps every `JSONB` column (`project_configs.parsed_json`, `integrations.config_json`) to `Map<String,Object>`, consistent with `AuditEvent.detailsJson` from Phase 1.
- `CoreSchemaRepositoryTest` — a `@SpringBootTest` + Testcontainers `postgres:17` suite with one round-trip per entity, built as a real FK chain (project → task → run → step/log/change/approval/PR, and integration → secret → credential) rather than orphan rows, plus a `flywayMigrateTwice_isIdempotent` test that re-invokes `Flyway.migrate()` inside a running context and asserts zero new migrations execute.
- `ddl-auto=validate` passes against the full 14-table schema (12 new + `users`/`audit_events` from Phase 1) — verified both by the test suite and by booting `talos-api` against `docker-compose.dev.yml`'s real Postgres and confirming all 15 tables (14 + `flyway_schema_history`) and both migration rows exist via `psql`.

## What is stubbed

- No business logic anywhere in these modules yet — no controllers, no services, no state-machine validation, no transition rules. Every entity is a plain persistence mapping; Phase 3 (`dev.talos.projects`) is the first to add real service/controller behavior.
- `Project`, `Task`, `AgentRun`, etc. have getters only, no setters/mutation methods, except `ProjectConfig.setActive()` (needed to enforce "at most one `is_active=true` row per project" once something writes a new config version — the one piece of Section 9.2 that names a mutation explicitly). Every other entity gets its mutation API in the phase that actually implements the mutating endpoint, to avoid guessing at method shapes a real service might not need.
- `created_at`/`updated_at` on every entity are `insertable=false, updatable=false`, deferring to the DB's `DEFAULT now()`. This means `updated_at` currently never changes after insert on any table — correct for now since nothing updates rows yet, but Section 9.1's "`updated_at` maintained by the API on every mutation of mutable tables" isn't actually true yet for any entity. Each phase that adds a real update path needs to flip that column to `updatable=true` and set it explicitly; noting this so it isn't silently forgotten.

## Deviations from the plan

- **Removed `@Lob` from every `String`/`byte[]` field mapped to a `TEXT`/`BYTEA` column.** This was a real bug, not a stylistic choice: Hibernate's default JPA mapping for `@Lob String` on PostgreSQL targets `Types.CLOB`, which Hibernate implements via Postgres's `oid` large-object mechanism — not the `text` type the migration actually declares. `ddl-auto=validate` failed immediately with `wrong column type encountered in column [message] in table [agent_run_logs]; found [text (Types#VARCHAR)], but expecting [oid (Types#CLOB)]`, and the same mismatch existed on every other `@Lob`-annotated field (`AgentRun.prompt`/`summary`/`errorMessage`, `Approval.notes`, `ProjectConfig.configYaml`, `Task.description`, `AgentRunStep.summary`, and `SecretValue`'s two `byte[]` fields against `BYTEA`). Fixed by dropping `@Lob` everywhere — Postgres `TEXT`/`BYTEA` have no meaningful size ceiling, so Hibernate's un-annotated default mapping (`VARCHAR`-family / `VARBINARY`-family) is both correct and exactly what the DDL declares. Not a plan deviation in the schema sense (the DDL is untouched); recorded here because it's a non-obvious JPA/Postgres interaction the next phase should know about before adding any new `TEXT` column.

Both the module-placement judgment calls above and the `@Lob` fix were resolved without stopping to ask — the DDL, enum lists, and module list from Section 6.2/9.2/9.3 are followed exactly; only entity/package-internal implementation choices needed filling in.

## Acceptance criteria (Section 16, Phase 2)

| Criterion | Status |
|---|---|
| Clean `flyway migrate` on an empty DB | ✅ verified via `docker compose up` against a fresh Postgres volume and via the test suite's context startup |
| `ddl-auto=validate` passes | ✅ (after the `@Lob` fix above) |
| CHECK constraints match Section 9.3 exactly | ✅ every enum's values transcribed directly from the DDL/Section 9.3 list |
| Repository round-trips per entity (test) | ✅ `CoreSchemaRepositoryTest`, all 12 new entities |
| Migrate-twice idempotency (test) | ✅ `flywayMigrateTwice_isIdempotent` |

## Verification

- `./gradlew build` — BUILD SUCCESSFUL: 8 tests total (4 from Phase 1's `AuthControllerIntegrationTest`, 4 new in `CoreSchemaRepositoryTest`), all passing against Testcontainers `postgres:17`.
- `docker compose -f infra/docker-compose.dev.yml up -d --build` — `talos-api` reached `healthy`; confirmed via `psql` that all 15 tables exist and both `V001`/`V002` rows show `success=t` in `flyway_schema_history`; re-confirmed `POST /api/v1/auth/login` still works end to end (no regression from Phase 1). Compose stack and built image removed after verification.
- `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md` — zero results.
- Did not re-run `apps/web`/Python suites — untouched this phase.
- CI still hasn't run on GitHub Actions (no remote configured) — unchanged caveat from Phases 0–1.

## Notes

- The FK-chain approach in `CoreSchemaRepositoryTest` (real `project`→`task`→`run`→children rows, not isolated inserts with random UUIDs) was necessary, not just thorough — every child table's FK constraint would reject an orphan UUID, so this is close to the minimum test that actually exercises the schema's referential integrity rather than just each table in isolation.
- Worth remembering for every future entity with a `TEXT`/`BYTEA` column: don't reach for `@Lob` on Postgres. If a column genuinely needs Postgres's large-object storage (unlikely for anything in this schema — nothing here is multi-gigabyte), that would need explicit `@JdbcTypeCode` configuration instead of the bare `@Lob` default.

## Next

Phase 3 — Project registry: CRUD + `talos.yaml` parsing, API and UI. First phase where `dev.talos.projects` gets real controllers/services, and where `packages/project-config-schema/talos.schema.json` is actually written.
