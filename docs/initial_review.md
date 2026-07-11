# Initial review — implementation plan vs. codebase (2026-07-11)

Post-implementation gap review after Phases 0–16. Method: read `docs/src/talos-implementation-plan.md`
(Revision 2.1) end to end, walked every contract surface (schema, endpoints, events, adapters, UI
routes, security controls, CI) against the code, cross-checked the per-phase reports' disclosed
deviations for ones that were never closed, and re-ran every test suite from scratch.

## Verification baseline (all re-run for this review, not taken from reports)

| Suite | Result |
|---|---|
| `apps/api` `./gradlew test --rerun-tasks` (Testcontainers postgres/rabbitmq) | **175 tests, 0 failures** (4m56s) |
| `packages/agent-adapter-spec` `uv run pytest` (with Docker access) | **75 passed** — includes the real per-run-container Phase 11 tests |
| `apps/orchestrator` | **33 passed** |
| `apps/runner-supervisor` | **31 passed** |
| `apps/telegram-adapter` | **31 passed** |
| `apps/whatsapp-adapter` | **39 passed** |
| `apps/web` `ng test` (Node 22.23.1) | **14 passed** |
| Naming guard (`grep -ri agentos` scoped form) | clean |

Caveat on the adapter-spec suite: its container tests skip only when the `docker` *binary* is absent
(`shutil.which`). On a host where the binary exists but the socket is permission-denied they error
instead of skipping (reproduced in this review's sandbox). Harmless in GitHub Actions, but a
`skipif` on actual daemon reachability would be more robust.

## Critical — address before production

### 1. `POST /api/v1/webhooks/github` was never implemented, and it silently breaks workspace retention — **RESOLVED 2026-07-11** (see implementation log)

Section 10.2 lists the endpoint and Section 6.2 lists the `dev.talos.webhooks` module; neither
exists (`apps/api/src/main/java/dev/talos/` has no `webhooks` package, no controller maps
`/webhooks/**`, and `packages/contracts/openapi.yaml` doesn't document it). Only the config plumbing
exists: `TALOS_GITHUB_WEBHOOK_SECRET` is wired through `application.yml:47`,
`TalosProperties.java:13`, and `infra/dokploy/docker-compose.prod.yml:84` — configuration for a
feature that isn't there. No phase report ever claimed it was built (Phase 9 disclosed it as
deferred), but nothing after Phase 9 picked it up.

The knock-on effect is the serious part: `pull_requests.status` is set to `OPEN` at creation
(`integrations/PullRequest.java:37`) and **no code path ever moves it to `MERGED`/`CLOSED`** — the
webhook was the only planned mechanism, and there is no polling fallback. The Phase 11 retention
sweep excludes runs with an `OPEN` PR (`runs/RunService.java:358`), so every run that produces a PR
keeps its workspace under `/var/talos/workspaces` forever, even after the PR merges. On a single
VPS this is unbounded disk growth on the volume that also holds provider homes. Fix by implementing
the webhook (preferred, it's in the plan) or a PR-status poll in the retention path.

### 2. No way to start an agent run from the dashboard — **RESOLVED 2026-07-11** (see implementation log)

Section 15 ("start agent run from a card"), Section 6.1, and MVP definition-of-done items 3–4
assume runs are started from the UI. `POST /api/v1/tasks/{id}/start-run` is fully implemented and
tested API-side, but nothing in `apps/web` outside the generated client references it — no button
on `TaskCard`, `TaskDrawer`, or anywhere else. Phase 4's report deferred the button to Phase 5;
Phases 5–6 logged "no start-run button in apps/web yet" as a known blocker; it was never closed.
Today a run can only be started via curl/smoke-script or the Telegram/WhatsApp adapters. For a
product whose core loop is "move card → run agent → review", this is the single biggest functional
gap.

### 3. Latest ~9 commits have never run through CI — **RESOLVED 2026-07-11** (pushed; two CI-only test failures fixed, see implementation log)

`main` is 9 commits ahead of `talos/main` (the only remote). Everything from roughly Phase 13
onward (memory, cost tracking, RBAC, MinIO) has only been tested locally; the GitHub Actions
workflow — including the smoke job, Trivy scans, and the six-image docker-build matrix — has not
exercised them. Push and confirm CI green before calling the build production-ready.

## High — significant plan gaps

### 4. Approval expiry and reminders are inert — **RESOLVED 2026-07-11** (reminder-only semantics, operator-decided; see implementation log)

Section 8.2 requires a reminder event after 24h in `WAITING_APPROVAL`, and Section 6.2 lists a
`dev.talos.notifications` module (log-only in MVP). Neither exists. `approvals.expires_at` is
stamped on creation but nothing ever reads it; the `EXPIRED` status exists only in the
`ApprovalStatus` enum — no scheduler, no transition, no event. Every phase from 5 onward disclosed
this and none closed it. Consequence: approvals can sit pending forever with no signal, and the
`EXPIRED` state in the DDL is unreachable.

### 5. Runner supervisor endpoints are unauthenticated (open Phase 11 deviation) — **RESOLVED 2026-07-11** (see implementation log)

`Settings.internal_api_token` exists in `apps/runner-supervisor/.../config.py` but `app.py`
enforces nothing on any endpoint — `POST /runs/{id}/execute` will execute an arbitrary command for
any caller that can reach port 8081. The security boundary is network topology alone (documented in
`docs/security-model.md` §6 and the Phase 11 report, deviation 1). Two sharpening facts: (a) the
dev compose publishes `8081:8081` to the host, so on a dev box anything that can reach the host can
drive the runner; (b) the prod compose publishes no ports, so the boundary holds there — but a
single shared-token check (the token is already distributed to both sides) is cheap and removes an
entire class of "anything on the internal network" risk. Do this before production.

### 6. Dashboard is missing whole plan-mandated screens — **RESOLVED 2026-07-11** (see implementation log)

Implemented routes (`apps/web/src/app/app.routes.ts`): login, projects, project detail, board,
run detail, review. Missing versus Section 15 / later phases:

- **Command Center (`/`)** — the route redirects to `/projects`. No active-runs/pending-approvals/
  recent-failures overview, and therefore also no surface for the Section 11 DLQ alert
  ("alert surfaced in Command Center" — nothing consumes `talos.dlq` for alerting at all).
- **Integrations page (`/integrations`)** — API is complete (`IntegrationController` + `/test`);
  no UI. Disclosed in Phases 9/10, never closed. Configuring GitHub/Dokploy/provider credentials
  currently requires raw API calls.
- **Approvals inbox** — `GET /api/v1/approvals` has no UI consumer; reviewers must know a run id to
  reach `/review/:runId`. Compounded by #4 (no reminders) and no Command Center: a pending approval
  is invisible unless you're watching the specific run.
- ~~**Phase 14 surfacing** — plan: "a dashboard cost widget", recommendations "surfaced in the task
  form and Command Center". `CostController`/`RecommendationController` exist; zero UI references.~~
  **Correction (2026-07-11): stale, this was already done.** `apps/web/src/app/projects/
  project.store.ts` (`loadMonthlyCosts`/`loadRecommendations`) and
  `project-detail.page.html`/`task-form-dialog.component.html` render both, committed in `16718a9`
  (Phase 14), before this review was written. Verified live in a browser, not just by reading code.
- **User management (Phase 15) and memory browse (Phase 13)** — API-only, disclosed in their phase
  reports as deliberate; listed here for completeness.

Overall the frontend has 14 tests against a 175-test backend; the plan's per-PR frontend gate
(Kanban movement, run rendering, approval actions, form validation) is nominally covered but thin.

### 7. `talos-web` is absent from the dev compose — **RESOLVED 2026-07-11** (see implementation log)

Section 1.2's definition of done: every MVP assertion demonstrable on a fresh checkout with
`docker compose -f infra/docker-compose.dev.yml up` — item 1 starts "through the UI". The compose
file's own header says "talos-web still to come", and the README (line 49 area) documents running
the dashboard separately via `npm start`. The prod compose *does* include `web`, and
`apps/web/Dockerfile` builds — so this is a small wiring gap in dev compose, but it means the
stated MVP acceptance command doesn't actually deliver the product surface.

## Medium

### 8. Missing minor endpoints from Section 10.2 — **RESOLVED 2026-07-11** (operator decision, see implementation log)

- `POST /api/v1/runs/{id}/rerun-tests` — in the plan's endpoint table, in no phase's task list,
  never implemented, absent from `openapi.yaml`. **Decision: dropped, not implemented.**
  Investigating the "full reopen" semantics (the only variant the operator considered worth
  building) surfaced two real bugs a naive implementation would have shipped: reentering
  `WAITING_APPROVAL` creates a second `PENDING` `Approval` row alongside the already-decided one
  (`RunService.createApproval` has no uniqueness check/upsert), and a re-approval would call
  `handle_approval_decided`'s push/PR path again with no idempotency guard, risking a second PR
  against an already-merged branch. It also needs a wholly new `COMPLETED|FAILED -> RUNNING_TESTS`
  transition edge absent from Section 8.2's table, and only works inside the 7-day retention
  window before the workspace is hard-deleted. Given the effort-to-value ratio and the safety risk
  of the duplicate-PR path, the operator chose to strike it from scope rather than build it now.
  `docs/src/talos-implementation-plan.md` is left untouched (same convention as the Gradle
  deviation: the plan doc is the frozen source of truth, deviations are recorded here and in the
  implementation log, not by hand-editing the compiled doc).
- `GET /api/v1/projects/{id}/runs` — deliberately superseded by `GET /api/v1/runs?projectId=`
  (disclosed in Phase 3/5 reports). Fine functionally; the plan was never amended. No further
  action taken (same reasoning: this is a disclosed, low-risk deviation, not a bug).

### 9. No OpenAPI drift check in CI — **RESOLVED 2026-07-11** (see implementation log)

Section 10.1: "a CI check diffs the running API's springdoc output against it
[`packages/contracts/openapi.yaml`]". No such job exists and springdoc isn't even a dependency of
`apps/api` — the contract file is maintained entirely by hand. The generated Angular client keeps
web honest against the YAML, but nothing keeps the YAML honest against the running API. Given how
many post-MVP endpoints were added (memory, costs, users, chat), drift risk is real.

### 10. `TALOS_MAX_ACTIVE_RUNS` is read but never enforced — **RESOLVED 2026-07-11** (see implementation log)

`orchestrator/config.py:35` parses it; no other module references it. Concurrency is actually
bounded by `prefetch_count=1` on the single consumer, so today the setting is silently ignored —
setting it to 2 changes nothing. Either wire it (a semaphore around pipeline execution / prefetch
value) or document that concurrency is fixed at 1 per orchestrator replica.

### 11. Nothing consumes the DLQ

The orchestrator declares `talos.dlq` and binds it (`main.py:71-72`), delivery-limit dead-lettering
works — but no consumer, metric, or alert watches it. A poisoned run request dies silently after 3
redeliveries. Related to the missing Command Center (#6); at minimum a queue-depth check belongs in
an ops runbook.

## Low / housekeeping

- **Adapter env examples**: `apps/telegram-adapter` and `apps/whatsapp-adapter` have no
  `.env.example`, unlike the four core apps (Appendix A convention: "every variable ships with a
  commented entry in the relevant `.env.example`").
- **`GeminiCliAdapter`** remains a `NotImplementedError` stub — correct per Section 7.4 (backlog),
  just noting it's the only stub left.
- **Board reordering** can accumulate duplicate `board_position` values within a column (single-task
  move endpoint renumbers only the dragged task) — Phase 4 known issue, still open, cosmetic.
- **Login rate limiter trusts first `X-Forwarded-For` hop** (Phase 11 deviation 4) — safe behind
  Traefik as documented; becomes a bypass if the API is ever exposed directly. Keep the constraint
  visible in deployment docs.
- **JWT revocation** is not real-time on deactivation/role change (Phase 15 deviation) — up to 24h
  of residual validity. Accepted for a small-team install; revisit if user count grows.
- **Cost capture for OpenCode/OpenHands** returns nulls (Phase 14, deliberate pending verified event
  schemas) — cost dashboards under-report when those adapters are used.
- **Scheduled DB backups**: the Phase 11 restore drill validated the mechanism; no scheduled
  `pg_dump` job exists. Operational task before production.

## What was checked and found sound

- Schema: `V001`–`V008` match Section 9.2/9.3 (enums, CHECK constraints, deferred-table discipline —
  `project_environments` in V004, memory in V005, all post-MVP tables in their own migrations).
- All four communication paths and only those four; neither Python service opens a DB connection;
  `/internal/v1` token-gated on the API side.
- Approval gating: push/PR/deploy require an `APPROVED` row server-side, covered by tests
  (`ApprovalControllerIntegrationTest`, `ApprovalSelfApprovalTest`, git-token 409 tests); the runner
  refuses pushes to the default branch (`git_push.py:42`).
- Run/task state machines, per-state timeouts, reaper, crash recovery (`ORPHANED_BY_RESTART`), Redis
  lock, DLQ wiring with `x-delivery-limit: 3`, Redis-keyed `event_id` idempotency — all present and
  matching Sections 6.3/8.2/11.
- SSE contract: named events, heartbeat, `Last-Event-ID` backfill from `agent_run_logs`
  (`RunController.java:122-127`), EventSource polyfill for JWT auth.
- Adapter layer: all Phase 12 adapters real, contract suite (7.2's six points) green per adapter,
  capability preflight checks, secret masking at emission, per-run Docker execution with volume
  subpaths and the isolated `talos_run_network` — the full 75-test suite passes against a real
  daemon.
- Chat adapters: allow-list + rejected-sender audit, HMAC verification (WhatsApp), no approval
  actions exposed over chat, least-privilege service accounts via `IntegrationScopeFilter`
  superseded by Phase 15 RBAC.
- RBAC: role×endpoint matrix tests, self-approval prohibition with audited OWNER override, seeded
  admin maps to OWNER.
- CI: naming guard, per-app builds, six-image docker matrix, worker images, compose-based smoke job,
  Trivy dependency + container scans (action pinned to a commit SHA).
- Secrets: AES-256-GCM store isolated in `dev.talos.secrets`, no REST exposure, prod compose fully
  parameterized (no committed production secrets), login rate limiting.

## Suggested order of attack

1. Push to remote; confirm CI green (#3).
2. GitHub webhook + PR status transitions, unblocking retention (#1).
3. Start-run button on the board/task drawer (#2) — small change, unlocks the core loop.
4. Runner supervisor token check (#5) and `web` service in dev compose (#7).
5. Approvals inbox + Command Center minimal version (#6), folding in a DLQ depth indicator (#11).
6. Approval expiry/reminder job (#4).
7. The Medium/Low items as a cleanup pass, amending the plan where reality deliberately diverged
   (`projects/{id}/runs`, rerun-tests, `TALOS_MAX_ACTIVE_RUNS`).
