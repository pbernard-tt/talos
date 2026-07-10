# Phase 10 report — Dokploy integration

## What works

- **Two-tier approval-gated deploy.** `POST /api/v1/runs/{id}/deploy` re-resolves the project's
  active `talos.yaml` `deploy:` block, syncs a `project_environments` row from it, and either
  creates a `PENDING` `DEPLOY`-type `Approval` (production always, or whenever
  `deploy.approval_required: true`) or triggers immediately (staging with
  `approval_required: false`). The deploy provider is never called while a `DEPLOY` approval is
  pending — verified both by a Mockito `never()` assertion in
  `DeployControllerIntegrationTest.deploy_productionEnvironment_...` and live against the running
  stack (see Verification).
- **`dev.talos.integrations.DeployProvider`/`DokployDeployProvider`.** `java.net.http.HttpClient`
  client for the real Dokploy REST API — `POST /api/application.deploy` to trigger,
  `GET /api/deployment.all?applicationId=` to poll, `x-api-key` auth. Verified against Dokploy's
  current documentation via Context7 (`/websites/dokploy`) rather than guessed, including the
  `idle|running|done|error` status enum (confirmed via the analogous `postgres.changeStatus`
  endpoint, since `deployment.all`'s exact per-item response shape isn't fully documented).
- **`DeployStatusPoller`**, mirroring `dev.talos.runs.RunReaper`'s `@Scheduled`/direct-call-in-test
  style: polls every `RUNNING` `project_environments` row every 30s, flips it to
  `SUCCEEDED`/`FAILED` on a terminal Dokploy state, and publishes `deploy.completed`/`deploy.failed`.
- **`ApprovalService.decide()` now branches on `approvalType`** — `RUN_RESULT` keeps its exact
  Phase 8/9 behavior (drives the run through `APPROVED`/`REJECTED`); `DEPLOY` instead calls
  `DeployService.triggerNow` on approve, with reject/request-changes just recording the decision.
- **Angular:** a compact "Deploy" section on `RunDetailPage`, visible once a run is `COMPLETED`:
  shows current deploy status, a Deploy button, and (when a `DEPLOY` approval is pending) inline
  approve/reject reusing `ApprovalActionDialogComponent` from Phase 8.
- **`docs/deployment.md`** is now a real operator runbook (prerequisites, Dokploy Compose app setup,
  the full Appendix A env var mapping, GitHub/Dokploy integration setup via `curl`, rollback
  procedure), and **`infra/dokploy/docker-compose.prod.yml`** is a real production reference
  composition covering all Section 18 services — together these are what make "a fresh operator can
  deploy Talos to Dokploy from `docs/deployment.md` alone" true rather than aspirational. The four
  services' `Dockerfile`s were already production-grade (multi-stage, non-root, health-checked)
  from earlier phases — no changes needed there.
- **A real transaction-boundary bug was found and fixed via the live walk, not by any unit test.**
  `DeployService.triggerNow` and `ApprovalService.decide()`'s three public entry points were all
  `@Transactional`. Since `deployProvider.trigger(...)` is an external HTTP call, a Dokploy failure
  there threw an unchecked exception that rolled back the *entire* enclosing transaction — including
  the "mark FAILED" write `triggerNow` made in its own catch block, and (worse) the approval's
  `APPROVED` status itself in the `ApprovalService` path. A human's approval action would appear to
  silently vanish (reverted to `PENDING`) whenever the deploy trigger failed. Fixed by removing
  `@Transactional` from `requestDeploy`, `triggerNow`, and `approve`/`reject`/`requestChanges`; each
  individual write already commits on its own via Spring Data's per-method transactions, and
  `RunService.transitionRun` (a separate bean, unaffected) keeps its own atomicity for the
  `RUN_RESULT` run-transition. A regression test
  (`DeployControllerIntegrationTest.deploy_providerTriggerThrows_marksEnvironmentFailed_notSilentlyRolledBack`)
  now covers this.

## Documented deviations

1. **Two-tier approval, not one.** Section 1.2's "approving the run is required before any push,
   pull request, or deploy action" is already structurally guaranteed (a run can only reach
   `COMPLETED` via the `RUN_RESULT` approval enforced since Phase 8/9). Section 12.2 separately
   requires approval for "production deploys" as its own control, and the schema already reserved a
   distinct `'DEPLOY'` `approval_type` for exactly this — resolved as a second, deploy-specific
   approval gate layered on top of the (already-guaranteed) run approval.
2. **`approvals.environment`** (new nullable column, `V004__deploy.sql`) — a `DEPLOY` approval needs
   to remember which environment it gates; same reasoning as Phase 8's `diff_patch`/`matched_pattern`
   additions.
3. **`DeployProvider`/`DokployClient` live entirely in talos-api, no orchestrator involvement.**
   Unlike Phase 9's git push, triggering/polling a Dokploy deployment is a pure REST call with no
   filesystem work — no structural reason to route it through the orchestrator/RabbitMQ. Resolves
   Section 11's ambiguous "API/orchestrator" producer for `deploy.*` as talos-api alone, consistent
   with the Phase 9 GitHub-PR-creation precedent.
4. **No live Dokploy instance available.** `DokployDeployProviderTest` uses an embedded
   `com.sun.net.httpserver.HttpServer` mock (same approach as Phase 9's `GitHubClientTest`) for the
   required "mock-Dokploy integration test covers success and failure." The live walk below
   exercises the full approval-gate flow and a real (intentionally unreachable) HTTP call, so the
   failure path — and the transaction bug above — was still caught live, just not against a real
   Dokploy server.
5. **No new Angular Integrations page** (same gap Phase 9 left for GitHub) — a Dokploy integration
   is configured via a direct `POST /api/v1/integrations` call (documented in `docs/deployment.md`
   step 3), not a dashboard form.

## Stubbed / deferred

- The Angular Integrations page (see deviation 5).
- A `deployments` history table (only the single latest `project_environments` status is tracked,
  not a full history of past deploys) — not requested by the plan, which names only
  `project_environments`.
- Phase 11 (per-run Docker execution, security hardening) remains untouched.

## Verification

- `apps/api`: `sg docker -c "./gradlew test"` — full suite green, including new
  `DokployDeployProviderTest` (mock HTTP server: trigger success/400/401, all four status mappings),
  `DeployStatusPollerTest` (direct-call style mirroring `RunReaperTest`), and
  `DeployControllerIntegrationTest` (422 on a non-`COMPLETED` run, 422 `NO_DEPLOY_CONFIGURED`, the
  production approval-gate proof with a `never()` provider-call assertion, the staging
  auto-deploy-with-no-approval-row path, and the transaction-boundary regression test).
- `apps/web`: `npm run build` and `npx ng test --watch=false` — both green (no new spec file added
  for the Deploy section; no acceptance criterion in this phase names a required new UI test).
- `docker compose -f infra/docker-compose.dev.yml up -d --build`: live walk — configured a
  `dokploy` integration, created a project with a `production` `deploy:` block requiring approval,
  walked a run to `COMPLETED`, requested a deploy (created a `PENDING` `DEPLOY` approval,
  confirmed `GET /runs/{id}/deploy` showed `lastDeployStatus: null` — never triggered), approved it
  (the intentionally-unreachable Dokploy URL failed with `502 DOKPLOY_UNREACHABLE`), and confirmed
  — after the transaction-boundary fix above — that the approval correctly stayed `APPROVED` and
  `project_environments.lastDeployStatus` correctly recorded `FAILED` rather than silently
  reverting. Grepped `talos-api`'s logs for the fake Dokploy API key used in this walk: zero matches.
- Naming guard (`grep -ri agentos ...`) returns nothing.
- **Not checked:** a live deploy against a real Dokploy instance (no instance available — see
  deviation 4); interactive browser verification of the Deploy section UI (no browser automation
  tool available this session — verified instead via `ng build`/`ng test` and the live API walk
  proving the underlying data and gating logic are correct).
