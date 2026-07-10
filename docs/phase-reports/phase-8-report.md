# Phase 8 report — Review and approval flow

## What works

- **Post-run policy scan (`dev.talos.policy`).** A bundled default `policy.yaml` (Section 12.3's
  example, classpath resource, overridable via `TALOS_POLICY_FILE`) drives `PolicyScanService`,
  which runs automatically the moment a run transitions to `WAITING_APPROVAL`. It matches every
  `git_changes` row's file path against gitignore-style glob patterns (bare patterns match the
  basename at any depth, e.g. `.env*` flags `backend/.env`) and every `agent_run_logs` message
  against command substrings, sets `git_changes.matched_pattern` + `risk_flagged` on matches, sets
  `agent_runs.review_status`, and writes a `run.policy.scanned` audit event with the full matched-
  pattern lists.
- **Auto-created approvals.** The same transition inserts a `PENDING` `approvals` row
  (`RunService.createApproval`) and publishes `approval.requested`
  (`packages/contracts/events/approval.requested.json`, new this phase).
- **Approval actions (`dev.talos.approvals`).** `ApprovalService`/`ApprovalController` add
  `GET /api/v1/approvals` (now filterable by `runId`, a Phase 8 addition needed by the Review
  page), `GET /api/v1/approvals/{id}`, and `POST .../approve|reject|request-changes`. Approve
  drives the run to `APPROVED`; reject and request-changes both drive it to `REJECTED` (Section 8.2
  has no run status for "changes requested" — the `Approval` row keeps that distinct outcome and
  notes, but the run takes the only legal edge back to a re-workable state, and the task returns to
  `READY`). Every decision publishes `approval.decided`
  (`packages/contracts/events/approval.decided.json`, new this phase; not yet consumed — Phase 9
  wires the orchestrator's push/PR to it).
- **Enforcement gap closed.** `POST /internal/v1/runs/{id}/status` (orchestrator-only, service
  token) now rejects `APPROVED`/`REJECTED` targets with `422 APPROVAL_REQUIRED_FOR_TRANSITION`.
  Before this phase, nothing stopped the orchestrator's service token from setting either status
  directly — Section 8.2 marks that edge "API, human decision" only, and this is exactly what the
  phase's server-side enforcement acceptance criterion asks for. Verified with a live smoke test
  against the running container: the internal call now 422s, and the run only reaches `APPROVED`
  via `/api/v1/approvals/{id}/approve`.
- **Diff serving.** `GET /api/v1/runs/{id}/diff` returns `{files, diff}`. The runner supervisor
  already computed the full unified diff text; the orchestrator now forwards it
  (`record_changes(..., diff_patch=...)`) instead of discarding it, and it's persisted onto a new
  `agent_runs.diff_patch` column.
- **Angular:** `RunDetailPage` (`/runs/:id`) — status/step timeline, changed files, live logs, and
  cancel — and `ReviewPage` (`/review/:runId`) — risk panel, file list with matched patterns,
  unified diff, and approve/reject/request-changes actions behind a confirmation dialog restating
  the action (Section 15's UX rule), each requiring notes for reject/request-changes. Live logs use
  a hand-rolled `fetch` + `ReadableStream` SSE reader rather than native `EventSource`: `EventSource`
  cannot send the `Authorization: Bearer` header this endpoint requires (JWT-only auth, no cookie
  session), so a browser-native `EventSource` cannot authenticate against it at all.
- Live acceptance walk (against the running `docker compose` stack, not just Testcontainers):
  created a run, walked it through the state machine, recorded a change to `backend/.env`, and
  confirmed all three Phase 8 acceptance criteria directly via curl — `reviewStatus` came back
  `RISK_FLAGGED` with `matchedPattern: ".env*"` on the diff endpoint, the internal bypass attempt
  returned 422, and approving via `/api/v1/approvals/{id}/approve` moved the run to `APPROVED`.

## Documented deviations (see plan discussion before implementation; all are additive, non-breaking)

1. **`agent_runs.diff_patch TEXT` and `git_changes.matched_pattern VARCHAR(200)`** — new columns
   (`V003__review_and_approvals.sql`), added because Section 9.2's DDL predates Phase 8 and has no
   column for either; the plan explicitly deferred "how" diff serving works to this phase. Confirmed
   backward-compatible against the existing dev Postgres volume (Flyway migrated `002 -> 003` clean
   on a fresh container against pre-existing data).
2. **`talos.yaml rules.forbidden`/`rules.require_approval_for` merge** — these use semantic tokens
   (`production_deploy`, `auth_changes`, ...) while `policy.yaml` uses literal globs/substrings
   (12.3); the plan doesn't reconcile the two vocabularies. They're merged as additional literal
   pattern strings alongside `policy.yaml`'s lists, so a project can add its own literal
   globs/substrings via `talos.yaml`; semantic tokens simply never match anything. Not exercised by
   this phase's acceptance criteria, which only test `policy.yaml`'s own patterns.
3. **CDK virtual scrolling for the live log pane** (Section 15: "logs can reach tens of thousands of
   lines") is not implemented — a plain auto-scrolling pane is. Not covered by this phase's
   acceptance criteria; flagged as follow-up work before real large-log runs land in the UI.
4. **Approval reminder event** ("after 24h", Section 8.2's table) — `expiresAt` is stamped
   (`createdAt + 24h`) but no reminder job runs yet, consistent with `notifiers` being an unbuilt
   consumer everywhere else in the system today.

## Stubbed / deferred

- Push/PR after `APPROVED` (Phase 9) — `approval.decided` is published but has no consumer yet.
- Deploy triggers (Phase 10) remain untouched.
- Generic artifact storage (`POST /internal/v1/runs/{id}/artifacts`, transcript.txt/test-report.*)
  is still not implemented; only the diff text got a durable home this phase, scoped to what the
  Review Center actually needed.

## Verification

- `apps/api`: `sg docker -c "./gradlew test"` — all 13 test classes green (added
  `PolicyMatcherTest` and `ApprovalControllerIntegrationTest`; updated
  `RunControllerIntegrationTest` to reach `APPROVED` via the approval endpoint and to prove the
  internal endpoint rejects `APPROVED`/`REJECTED` directly).
- `apps/orchestrator`: `UV_CACHE_DIR=/tmp/talos-uv-cache uv run pytest` — 18/18 passed, including
  the updated diff-forwarding assertion in `test_pipeline.py`.
- `apps/web`: `npm run build` and `npx ng test --watch=false` — both green (4 test files, 14 tests,
  including the new `review.page.spec.ts` covering approve/reject/request-changes dialog flows).
- `docker compose -f infra/docker-compose.dev.yml up -d --build`: rebuilt image, Flyway applied
  `V003` cleanly against the existing dev database, `talos-api` reports `UP`. Live curl walk (see
  above) confirmed all three Phase 8 acceptance criteria against the running stack.
- Naming guard (`grep -ri agentos ...`) returns nothing.
- **Not checked:** interactive browser verification of `RunDetailPage`/`ReviewPage` — no browser
  automation tool was available in this session. Verified instead via `ng build`/`ng test`
  (compiles, unit tests pass) and a full live API walk proving the data the pages render is correct
  end to end; the rendering itself was not visually inspected. Recommend a manual click-through
  before this ships past dev.
