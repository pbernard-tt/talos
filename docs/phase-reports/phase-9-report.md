# Phase 9 report — Git push and PR workflow

## What works

- **Approval-triggered push/PR (`Section 8.2` APPROVED → COMPLETED).** Approving a run
  (`ApprovalService.approve`) already published `approval.decided` to `talos.orchestrator.approvals`
  since Phase 8, but nothing consumed it. The orchestrator now binds that queue and, on `status ==
  "APPROVED"`, drives the run through `PUSH`/`PR` steps: it fetches a short-lived GitHub token from
  a new internal-only endpoint, asks the runner supervisor to commit/push the branch, then asks
  talos-api to open the PR (which stores the row and completes the run atomically).
- **`dev.talos.secrets.SecretService`.** AES-256-GCM encrypt/decrypt keyed by `TALOS_SECRETS_KEY`,
  built on the `SecretValue`/`IntegrationCredential` entities that existed since Phase 2 but had no
  service layer. Key derivation is deferred to first use (not the constructor) so every other test's
  Spring context, which component-scans this `@Service` but doesn't set `talos.secrets-key`, keeps
  loading without needing that property.
- **`dev.talos.integrations`.** `GitHubClient`/`GitHubClientImpl` (`java.net.http.HttpClient`, no new
  dependency) for `testConnection`/`createPullRequest`; `IntegrationService`/`IntegrationController`
  implementing Section 10.2's `GET/POST /integrations`, `POST /integrations/{id}/test` (these were
  already in the plan's endpoint table, just unimplemented); `GitCredentialsService` (resolves the
  decrypted token for a run's push step, guarding `run.status == APPROVED`); `PullRequestService`
  (builds the PR body per Section 8.4's template, calls GitHub, stores `PullRequest`, publishes
  `pr.created`, completes the run).
- **New internal endpoints (Section 10.4).** `GET /internal/v1/runs/{id}/git-token` (409 unless
  `APPROVED` — this is the server-side enforcement behind "unapproved run cannot push") and
  `POST /internal/v1/runs/{id}/pull-request` (opens the PR, completes the run in one call so a PR
  can never be created without the run reaching `COMPLETED`, or vice versa).
- **Runner supervisor push step.** New `git_push.py`: stages all changes, commits only if the index
  differs from HEAD (agents' own commits are preserved verbatim, matching Section 8.4's "who
  commits" rule), pushes via a `GIT_ASKPASS` script fed the token only through a transient env var
  — never in the remote URL, never written to the workspace, never logged. New
  `POST /runs/{id}/push` endpoint. A non-fast-forward/rejected push returns `needsRebase: true`
  instead of raising, matching Section 8.4's "never auto-merge or force-push."
- **`GET /api/v1/runs/{id}/pull-request`** (404 until one exists) and a small `RunDetailPage`
  addition that shows the PR link once the run is `COMPLETED`.
- **Live acceptance walk** (against the running `docker compose` stack, not just Testcontainers):
  created a project pointing at a real local bare git repo (mounted into the runner-supervisor's
  own workspace volume, so no GitHub credentials were needed for this half), confirmed
  `git-token`/`pull-request` both return `409 RUN_NOT_APPROVED` before approval, approved the run,
  and confirmed the branch was actually pushed to the bare repo with the correct commit (verified
  via `git log` on the bare repo itself) — the `PUSH` step reached `COMPLETED`. The `PR` step then
  failed as expected (`UNSUPPORTED_REPO_URL`, since the bare-repo fixture isn't a real GitHub URL),
  correctly landing the run on `FAILED` with a clear error message rather than a silent hang.
  Grepped all four log surfaces (`talos-api`, `talos-orchestrator`, `talos-runner-supervisor`, and
  the persisted `agent_run_logs`) for the raw integration secret used in this walk: zero matches.

## Documented deviations

1. **Credential delivery is a new internal-only endpoint, not embedded in `/internal/v1/runs/{id}/context`.**
   `SecretValue`'s doc comment says "never exposed via REST — no controller or DTO may reference
   this entity." Read literally that's about `/api/v1/**`; Section 8.1 step 4 and 12.2 ("delivered
   process-env only") already require a decrypted secret to reach the runner process somehow, and
   there's no path for that besides an internal, service-token-authenticated endpoint. `GET
   /internal/v1/runs/{id}/git-token` is that endpoint, guarded to `APPROVED` runs only.
2. **`GitHubClient` lives in talos-api, not the orchestrator.** talos-api already holds the
   decrypted token in-process for `POST /integrations/{id}/test`; PR creation reuses that same
   in-process decrypt rather than shipping the token to the orchestrator a second time. The
   orchestrator only ever sees the token for the one runner-supervisor push call.
3. **Single global GitHub integration.** The DDL has no per-project FK to `integrations` (Section
   9.4 doesn't add one; Section 15/10.2 describe integrations as global), so `IntegrationService`
   resolves "the" enabled `type='github'` integration and uses it for every project. Matches the
   MVP's explicit "GitHub only" scope; a real multi-repo/multi-org setup would need this revisited.
4. **NEEDS_REBASE reuses `error_message`.** Section 9.3's `RunStatus` enum is closed and there's no
   dedicated "review notes" column. A rejected push calls the existing
   `/internal/v1/runs/{id}/status` with `status=FAILED, errorMessage="NEEDS_REBASE: <reason>"` — no
   new schema, and the Review Center already renders `errorMessage` for failed runs.
5. **Live PR creation against a real GitHub repo was not exercised.** No scratch GitHub repo or PAT
   was available in this environment. This is explicitly optional in the plan ("optional manual
   e2e against a scratch repo"); the required "GitHub client against a mock server" test
   (`GitHubClientTest`, an embedded `com.sun.net.httpserver.HttpServer`) covers the actual HTTP
   contract instead, and the live walk above covers the push half of the flow against a real git
   remote. Recommend a manual PR-creation smoke test against a real scratch repo before this ships
   past dev.
6. **No Integrations page in Angular this phase.** Section 15's Integrations module isn't in Phase
   9's Files list; `POST /api/v1/integrations` is exercised via API/tests only (the same way Phase 8
   left the Integrations page unbuilt). A GitHub integration currently has to be configured via a
   direct API call, not the dashboard. Flagged as follow-up.

## Stubbed / deferred

- Deploy triggers (Phase 10) remain untouched; `DeployProvider` is still a no-op.
- `POST /api/v1/webhooks/github` (inbound webhook) is still not implemented — this phase only
  covers the outbound push/PR direction.
- The Angular Integrations page (see deviation 6).
- Approval reminder job (Section 8.2's "after 24h") — still just a stamped `expiresAt`, no job
  built, consistent with every other phase's disclosure of this gap.

## Verification

- `apps/api`: `sg docker -c "./gradlew test"` — full suite green, including new `SecretServiceTest`,
  `GitHubClientTest` (mock HTTP server), `IntegrationControllerIntegrationTest` (create/list/test,
  and a masking assertion that the raw secret never appears in either response body), and
  `RunControllerIntegrationTest`/`EventPublisherIntegrationTest` additions covering the full
  approve → git-token → push → pull-request → `COMPLETED` flow, the `pull_requests` row, the
  `pr.created` event validating against its new JSON Schema, and the two 409 "unapproved run cannot
  push" proofs.
- `apps/orchestrator`: `UV_CACHE_DIR=/tmp/talos-uv-cache uv run pytest` — 22/22 passed, including
  `handle_approval_decided`'s happy path, the `REJECTED`-is-a-noop case, the non-`APPROVED`-status
  race guard, and the `needs_rebase` → `FAILED`/`NEEDS_REBASE` path.
- `apps/runner-supervisor`: `uv run pytest` — 22/22 passed, including `test_git_push.py` (commit +
  push, agent-already-committed preserved, non-fast-forward → `needs_rebase`, refuses pushing the
  default branch) and two new `/runs/{id}/push` endpoint tests in `test_app.py`.
- `apps/web`: `npm run build` and `npx ng test --watch=false` — both green (no new spec file added
  for the PR-link display; no Phase 9 acceptance criterion requires one, matching how Phase 8 only
  added a spec for the page with an explicit "UI approval action test" requirement).
- `docker compose -f infra/docker-compose.dev.yml up -d --build`: live walk described above,
  covering both the 409 guard and the real push against a bare git repo, plus the four-surface log
  grep for the credential.
- Naming guard (`grep -ri agentos ...`) returns nothing.
- **Not checked:** live PR creation against a real GitHub repository (see deviation 5); interactive
  browser verification of the PR-link UI addition (no browser automation tool available this
  session — verified instead via `ng build`/`ng test` and the live API walk proving the underlying
  data is correct).
