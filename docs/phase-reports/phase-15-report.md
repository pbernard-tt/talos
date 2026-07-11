# Phase 15 report — Multi-user RBAC enforcement

Phase 15 is complete. The `OWNER`/`MAINTAINER`/`REVIEWER`/`VIEWER` roles that have existed in the
schema since Phase 1 are now really enforced server-side, replacing MVP owner-mode. User
management, self-approval prohibition, and role-aware UI hiding are all in place.

## What works

- **Enforcement mechanism.** `@EnableMethodSecurity` plus a `RoleHierarchy` bean
  (`SecurityConfig.roleHierarchy()`: `OWNER > MAINTAINER > REVIEWER > VIEWER`) so
  `@PreAuthorize("hasRole('MAINTAINER')")` also admits OWNER, matching Section 9.3's "each role
  implies the ones after it." `JwtAuthenticationFilter` already stamped `ROLE_<name>`
  `GrantedAuthority`s onto every request from Phase 1 onward; Phase 15 is the first thing that
  reads them. Endpoints with no `@PreAuthorize` require only authentication (VIEWER's read-only
  tier) — nothing new was added for reads, only for writes/admin actions.
- **The matrix.** VIEWER: read-only (unchanged). REVIEWER: adds `POST /approvals/{id}/approve`
  `/reject` `/request-changes`. MAINTAINER: adds project/task CRUD, `start-run`, `run/{id}/cancel`,
  and memory-document ingestion. OWNER: adds `/integrations` (all three endpoints), `/runs/{id}/deploy`,
  and `/users` (all three endpoints).
- **Denial path.** `@PreAuthorize` throws `AuthorizationDeniedException` from inside the
  proxied controller method invocation — found live that this is caught by Spring MVC's own
  exception resolution *before* it would ever reach a filter-chain-level `AccessDeniedHandler`, so
  a `dev.talos.auth.AuthorizationExceptionHandler` (`@RestControllerAdvice`) handles it: 403 plus a
  `role.access_denied` audit row (mirrors `IntegrationScopeFilter`'s existing shape). Verified with
  a `ProjectControllerIntegrationTest` regression (see Root cause below) and the new
  `RoleAuthorizationMatrixTest`.
- **User management.** New `dev.talos.auth` `UserController`/`UserService` (OWNER-only, class-level
  `@PreAuthorize`): `POST /users` creates with an operator-set password (see deviation below),
  `GET /users` lists, `PATCH /users/{id}` changes role and/or active status. An OWNER cannot change
  their own role or deactivate themselves through this endpoint (`CANNOT_MODIFY_OWN_ACCOUNT`) —
  prevents a lone OWNER locking the install out of user management.
- **Deactivation.** New `users.active` column (default `true`); `AuthService.login` folds the
  active check into the same password-match filter so a deactivated account gets the identical
  generic `INVALID_CREDENTIALS` response as a wrong password (no account-existence/status leak).
- **Self-approval prohibition, with an audited OWNER override.** New `agent_runs.requested_by`
  (set from `start-run`'s caller) flows into the auto-created `RUN_RESULT` approval's
  `requestedBy` (previously always `null`); `DEPLOY` approvals already carried it correctly.
  `ApprovalService.decide()` now takes the full `AuthenticatedUser` (not just a `UUID`) and rejects
  with `403 SELF_APPROVAL_FORBIDDEN` plus an `approval.self_approval_rejected` audit row when the
  acting user requested the underlying run/deploy — *unless* they're OWNER, in which case the
  decision proceeds and an `approval.self_approval_override` audit row is written instead.
- **Integration-scoped accounts stay narrowly bounded despite a higher role.** The Telegram/WhatsApp
  service accounts (Phase 12 Track B) are now seeded `MAINTAINER` (was `VIEWER`) because they must
  clear the new `hasRole('MAINTAINER')` gate on `POST /tasks` to keep creating tasks from chat.
  This is safe: `IntegrationScopeFilter` is a servlet filter that runs *before* method security and
  already denies every request outside its narrow allow-list regardless of role — the role only
  matters for the handful of endpoints that filter already lets through.
- **Upgrade path for already-seeded chat accounts (found live).** `IntegrationServiceAccountSeeder`
  is idempotent and never touched an existing row, so an install that seeded these accounts before
  Phase 15 would have them stuck on the old `VIEWER` default forever — task creation from chat
  would silently start 403ing the moment the API image is upgraded, with nothing in the logs to
  explain why. Caught by testing against this repo's own long-running dev `docker compose` stack
  (seeded weeks earlier under Phase 12). Fixed: the seeder now checks an *existing* account's role
  and upgrades `VIEWER` -> `MAINTAINER` in place, logging the upgrade; a fresh install seeds
  `MAINTAINER` directly as before. Verified against the real stack: both accounts flipped to
  `MAINTAINER` with `updatedAt` bumped, on the next `talos-api` restart, no manual intervention.
- **Frontend.** `LoginResponse` now carries `userId`/`email`/`role`; `AuthStore.hasRole(minimum)`
  mirrors the backend hierarchy for UI hiding only (never the real gate). "New Task"/"New Project"
  buttons hide below MAINTAINER; approve/reject/request-changes and deploy-approval decisions hide
  below REVIEWER; the deploy-trigger button and cancel-run button hide below OWNER/MAINTAINER
  respectively. Every hidden action is still blocked server-side if reached directly.
- **Contracts.** `packages/contracts/openapi.yaml` is at `0.15.0`: `Role`/`User`/`CreateUserRequest`/
  `UpdateUserRequest` schemas, `/users` + `/users/{id}` endpoints, `LoginResponse` fields, and `403`
  responses documented on every newly-gated operation.

## Root cause (found live, not by unit tests)

The first full-suite run after wiring `@PreAuthorize` turned three passing `ProjectControllerIntegrationTest`
cases into `500`s. The actual cause was two layered issues:

1. `AuthorizationDeniedException` (Spring Security's modern method-security denial type) is thrown
   *inside* the controller method invocation (Spring AOP wraps the handler), so it's caught by
   Spring MVC's own `@ExceptionHandler` resolution before it can ever reach a
   `SecurityFilterChain`-level `AccessDeniedHandler` registered via
   `exceptionHandling().accessDeniedHandler(...)`. An initial `RoleAccessDeniedHandler` wired that
   way was simply never invoked — the existing `GlobalExceptionHandler`'s catch-all
   `@ExceptionHandler(Exception.class)` intercepted it first and returned a generic `500`. Fixed by
   moving 403-plus-audit handling into a `@RestControllerAdvice` (`AuthorizationExceptionHandler`)
   instead, kept in `dev.talos.auth` rather than `dev.talos.common.GlobalExceptionHandler` so the
   low-level `common` package doesn't gain a reverse dependency on `auth`/`audit` types.
2. `ProjectControllerIntegrationTest` used class-level `@WithMockUser` (Spring Security Test's
   synthetic principal, default authority `ROLE_USER`) rather than a real login — harmless under
   owner-mode's "any authenticated request passes," but it doesn't satisfy `hasRole('MAINTAINER')`.
   Fixed by pinning it to `@WithMockUser(roles = "MAINTAINER")`.

## Documented deviations

1. **User creation, not invitation.** The plan says "create/invite"; this codebase has no
   mail/notification infrastructure anywhere to support a token-based email invite flow. Phase 15
   implements direct creation with an OWNER-set initial password (the same model `AdminSeeder`
   already uses), not an invite token.
2. **Deactivation and role changes are not retroactive to already-issued JWTs.** Section 12.2's JWT
   model is deliberately stateless (24h TTL, no revocation list). A deactivated or role-changed
   user's *existing* token keeps working — with its old role claim — until it naturally expires;
   only a fresh login is blocked/reflects the new role. Adding real-time revocation would mean a
   DB or Redis lookup on every authenticated request, a bigger architectural change than this
   phase's scope calls for; documented here as a known limitation rather than silently underbuilt.
3. **Integration-scoped service accounts seeded MAINTAINER, not VIEWER** — see "What works" above
   for the safety argument (`IntegrationScopeFilter` is the real bound, independent of role).
4. **Self-approval prohibition generalized to both RUN_RESULT and DEPLOY approvals**, not just
   "runs" as the plan's prose literally says — `Approval.requestedBy` is one field already shared
   by both approval types, and the governance principle (no self-approval) applies equally to both;
   splitting the enforcement by approval type would need to invent a reason not to.

## Stubbed / deferred

- Real-time JWT revocation on deactivation/role-change (see deviation 2).
- Email-based user invitations (see deviation 1) — pending future mail infrastructure.
- A dedicated "manage users" screen in the dashboard; user management is API-only for this phase
  (covered by `UserController`/the OpenAPI contract/Angular client), matching how Phase 13's memory
  management landed API-only too.

## Verification

- `apps/api`: full suite —
  `sg docker -c 'cd /home/paulb/Personal/Talos/apps/api && GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon test'`
  — BUILD SUCCESSFUL, zero regressions. New coverage: `RoleAuthorizationMatrixTest` (every
  MAINTAINER/REVIEWER/OWNER-tier representative endpoint rejects every role below its minimum and
  admits every role at or above it, backed by the `hasRole`+hierarchy semantics; read-only
  endpoints admit all four roles), `ApprovalSelfApprovalTest` (a MAINTAINER cannot approve their
  own requested run — 403 + audit row; a different REVIEWER can; an OWNER can override with the
  override itself audited; a VIEWER attempting approval gets 403 + `role.access_denied` audit row),
  `UserControllerIntegrationTest` (OWNER-only CRUD, duplicate-email 409, self-modification blocked,
  non-OWNER roles denied), `AuthControllerIntegrationTest` additions (`LoginResponse` carries
  role/email/userId; deactivated user cannot log in; seeded admin confirmed OWNER and active).
- `packages/contracts/openapi.yaml`: parsed with Python/YAML — version `0.15.0`; `/users` and
  `/users/{id}` present; `Role`/`User`/`CreateUserRequest`/`UpdateUserRequest` schemas present.
- `apps/web`: `npm run generate:api` succeeded (`users.service.ts` generated). `npm run build` and
  `npx ng test --watch=false` both succeeded (14 existing tests, no regressions; no new frontend
  spec files added, consistent with this repo's existing frontend test depth).
- `git diff --check` passed after stripping generated trailing whitespace/blank-line noise (the
  same generator quirk noted in the Phase 13/14 reports).
- Source-scoped naming guard returned no matches.
- Live `docker compose` stack: rebuilt `talos-api` against the new migration and confirmed
  `V007 - rbac` applied cleanly alongside the running `talos-postgres`/`talos-rabbitmq`/
  `talos-redis`/orchestrator/runner-supervisor/chat-adapter containers already up from Phase 14's
  verification; smoke-tested login (role/email/userId present in the response), `GET/POST /users`
  as OWNER, a VIEWER blocked from `POST /projects` (403), and confirmed the Telegram/WhatsApp
  service account role upgrade (see above) actually happened against this stack's real,
  weeks-old-seeded rows.
- Not checked: a live multi-operator install with real separate human accounts over a full 24h JWT
  lifetime (deactivation-timing behavior in deviation 2 is verified at the unit/integration level,
  not against a real elapsed-time scenario).
