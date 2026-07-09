# Phase 1 report — Backend foundation (auth, errors, audit)

## What works

- `V001__users_and_audit.sql` — `users` and `audit_events` tables, exact DDL from Section 9.2 (role/enum CHECK constraints per Section 9.3). Flyway runs it automatically on API startup against a real Postgres.
- `dev.talos.common` — `ErrorResponse`/`ApiException`/`GlobalExceptionHandler` (the `{"error":{"code","message","details"}}` envelope from Section 10.1 on every non-2xx response), `UuidV7` (application-side UUID v7 for every entity id), `TalosProperties` (binds the `talos.*`/`TALOS_*` config from Appendix A).
- `dev.talos.audit` — `AuditEvent` entity/repository and a write-only `AuditService`; the login flow calls it today, later phases (tasks, runs, approvals, pushes, deploys) call the same service.
- `dev.talos.auth` — `User`/`Role` entity, BCrypt password hashing, an idempotent `AdminSeeder` (`ApplicationRunner`) that seeds one `OWNER` user from `TALOS_ADMIN_EMAIL`/`TALOS_ADMIN_PASSWORD` on startup, a `JwtService` issuing/validating 24h HS256 JWTs signed with `TALOS_JWT_SECRET`, a `JwtAuthenticationFilter` + `SecurityConfig` requiring a valid JWT on every `/api/v1/**` route except `POST /auth/login`, a `JsonAuthenticationEntryPoint` shaping filter-chain-level 401s into the same error envelope, and `POST /api/v1/auth/login` (Section 10.2: `{email,password} -> {token,expiresAt}`).
- `talos-api` is now a real service in `infra/docker-compose.dev.yml`, built from `apps/api/Dockerfile`, wired to the postgres/rabbitmq/redis containers with dev-only placeholder secrets. Manually verified end to end against the live compose stack (see Verification).
- `AuthControllerIntegrationTest` — Testcontainers-backed (`postgres:17`) `@SpringBootTest`/MockMvc suite: seeded-credential login returns a JWT and writes the `audit_events` row, wrong-password login returns 401 `INVALID_CREDENTIALS`, an unauthenticated request to a protected path returns 401 `UNAUTHORIZED`, `/actuator/health` is public.
- `/actuator/health` public, `/api/v1/auth/login` public, everything else under `/api/v1/**` requires a JWT — enforced server-side by `SecurityConfig`, not just by convention.

## What is stubbed

- No RBAC enforcement — the `Role` enum and column exist, but every authenticated request passes regardless of role (Section 12.2: "MVP runs owner-mode"). Enforcement arrives with multi-user support, post-MVP.
- Only the admin user exists; there's no user CRUD, no additional accounts, no `/users` endpoint.
- `/actuator/health`'s Redis and RabbitMQ health indicators are disabled (see Deviations) — the endpoint currently only reflects the database and disk-space indicators.
- No `/internal/v1` namespace yet (Phase 5).
- `AuditService` is called from exactly one place (login). Task/run/approval/push/deploy audit calls arrive with those features in later phases.

## Deviations from the plan

- **Redis/RabbitMQ health indicators disabled.** Neither integration is load-bearing yet — RabbitMQ arrives Phase 5, Redis Phase 5/8 — so leaving their Spring Boot Actuator health indicators enabled would make `/actuator/health` falsely report `DOWN` (or 503) whenever those services aren't reachable, even though nothing depends on them yet. Disabled via `management.health.redis.enabled=false` / `management.health.rabbit.enabled=false` in `application.yml`; revisit when those integrations become real.
- **Admin seeding mechanism.** Section 12.2 says the admin is "seeded by migration," but Phase 1's only named migration file (`V001__users_and_audit.sql`) is plain SQL, and the password must be BCrypt-hashed before it reaches the `users` table — plain SQL can't do that, and Flyway placeholder substitution doesn't hash values either. Implemented as an idempotent Spring `ApplicationRunner` (`AdminSeeder`) instead: reads `TALOS_ADMIN_EMAIL`/`TALOS_ADMIN_PASSWORD` at startup, skips if either is unset or a user with that email already exists, otherwise hashes and inserts one `OWNER` row. Functionally equivalent to "seeded on boot," just not a `.sql` migration. Flagging this interpretation rather than treating it as self-evident, since the plan's literal wording points at Flyway.
- **`grep -ri agentos .` guard now also excludes `CLAUDE.md`/`AGENTS.md`.** Both files (added between Phase 0 and Phase 1) document this very naming rule, so the unscoped command started self-matching again the same way it already did against `docs/`. Extended the same scoping used since Phase 0 (`--exclude-dir=docs --exclude-dir=.github`) to also `--exclude=CLAUDE.md --exclude=AGENTS.md`, in `.github/workflows/ci.yml` and in the two files' own text. `docs/phase-reports/phase-0-report.md` and `docs/initial_implementation_log.md`'s Phase 0 entry are left as originally written (they were accurate when CLAUDE.md/AGENTS.md didn't yet exist) — this is a case where a later change alters an earlier statement's continued accuracy without making it wrong at the time, so per the log-writing rules it's recorded as a new entry rather than an edit to the old one.

None of these required stopping to ask — all are implementation-mechanism choices, not changes to any endpoint, schema, event, or enum in the plan.

## Acceptance criteria (Section 16, Phase 1)

| Criterion | Status |
|---|---|
| Login with seeded credentials returns a JWT | ✅ verified via `curl` against the live compose stack and via `AuthControllerIntegrationTest` |
| Unauthenticated request → 401 with the standard error body | ✅ same |
| Every login writes an `audit_events` row | ✅ verified via `psql` against the live compose stack (3 rows after 3 manual logins) and via the integration test |
| `/actuator/health` public | ✅ |
| MockMvc login success/failure and 401 shape (test) | ✅ `AuthControllerIntegrationTest` |
| Audit row asserted via Testcontainers PostgreSQL (test) | ✅ same |

## Verification

- `./gradlew build` — BUILD SUCCESSFUL: compiles clean (including under `-Xlint:deprecation`, added this phase after it caught a real deprecated-API usage — see Notes), all 4 tests in `AuthControllerIntegrationTest` pass against a Testcontainers `postgres:17`.
- `docker compose -f infra/docker-compose.dev.yml up -d --build` — all four services (`postgres`, `rabbitmq`, `redis`, `api`) started; `talos-api` reached Docker `healthy` within a few seconds of the infra containers being healthy.
- Manual end-to-end `curl` pass against the running stack: `GET /actuator/health` → 200; `POST /auth/login` with seeded admin credentials → 200 with `{token, expiresAt}`; same with a wrong password → 401 `INVALID_CREDENTIALS`; `GET` of an arbitrary unmatched path with no token → 401 `UNAUTHORIZED`; same path with a valid token → 404 `NOT_FOUND` (confirms the security layer isn't swallowing routing, and confirmed the `NoResourceFoundException` fix — see Notes).
- `docker exec talos-postgres psql -U talos -d talos -c "SELECT ... FROM audit_events"` — confirmed one `user.login` row per successful login, `actor_user_id`/`entity_id` correctly set to the admin's id.
- Compose stack torn down (`docker compose down`) and the built `talos-api` image removed after verification, consistent with Phase 0's practice of not leaving throwaway images around.
- `grep -ri agentos . --exclude-dir=.git --exclude-dir=docs --exclude-dir=.github --exclude=CLAUDE.md --exclude=AGENTS.md` — zero results.
- Did not re-run `apps/web`, `apps/orchestrator`, `apps/runner-supervisor`, or `packages/agent-adapter-spec`'s test suites — none of their files changed this phase, and Phase 0's report already establishes they were green.
- CI has still not run on GitHub Actions (no remote configured), same caveat as Phase 0. Unlike local `sg docker -c ...`, GitHub's `ubuntu-latest` runners have Docker available out of the box, so `AuthControllerIntegrationTest`'s Testcontainers usage should work unmodified there — not yet confirmed.

## Notes

- **Spring Boot 4.1 ships Jackson 3, under a new Maven coordinate *and* Java package: `tools.jackson.*`, not `com.fasterxml.jackson.*`.** `ObjectMapper` in `JsonAuthenticationEntryPoint` had to be imported from `tools.jackson.databind`. `jackson-annotations` (e.g. for `@JsonProperty`, not used yet in this codebase) is unaffected — only `jackson-core`/`jackson-databind` moved. Worth remembering the first time any DTO needs a Jackson annotation.
- **Spring Framework 7 deprecated `org.springframework.lang.NonNull`/`@Nullable`** in favor of JSpecify (`org.jspecify.annotations`), which is already on the classpath transitively. Added `-Xlint:deprecation` to `build.gradle.kts` permanently after it caught this in `JwtAuthenticationFilter` — worth keeping given how much of this stack (Boot 4/Framework 7/Jackson 3/Testcontainers 2) is new enough that "the obvious API" is sometimes already deprecated.
- **Testcontainers 2.0 (the version `spring-boot-testcontainers:4.1.0` was built against) renamed its module artifacts and moved classes.** `org.testcontainers:junit-jupiter`/`org.testcontainers:postgresql` no longer exist past 1.21.x — the 2.x equivalents are `org.testcontainers:testcontainers-junit-jupiter`/`org.testcontainers:testcontainers-postgresql`. `PostgreSQLContainer` moved from `org.testcontainers.containers` (now deprecated) to `org.testcontainers.postgresql`, and lost its generic self-type parameter (`PostgreSQLContainer`, not `PostgreSQLContainer<?>`).
- **A real bug, not just a test artifact:** the first version of `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` was intercepting Spring's own `NoResourceFoundException` (thrown for any unmatched route) and turning every 404 into a 500. Caught by the manual `curl` smoke test, not by the unit-scoped MockMvc tests (none of which hit a genuinely unmatched route with a valid token). Fixed by adding a specific `NoResourceFoundException -> 404 NOT_FOUND` handler before the catch-all. This will matter in every future phase that adds routes — any request to a real endpoint with a typo'd path would otherwise have silently come back as a misleading 500.
- Dev-only secrets committed in `infra/docker-compose.dev.yml` (`TALOS_JWT_SECRET`, `TALOS_ADMIN_PASSWORD`, etc.) are deliberately obvious placeholders, consistent with the `talos`/`talos` Postgres credentials already committed there since Phase 0 — never reused outside local development.

## Next

Phase 2 — Database and migrations: the full MVP schema from Section 9.2 (`V002__core_schema.sql`) and JPA entities/repositories for every remaining table, with `ddl-auto=validate` proving the mapping matches exactly.
