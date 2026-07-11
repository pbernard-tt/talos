# Phase 14 report — Cost tracking and recommendations

Phase 14 is complete. Per-run usage/cost is captured where the underlying adapter can report it,
persisted on `agent_runs`, aggregated per project/agent/month behind a dashboard cost widget, and
a project's run history now drives advisory-only agent/risk-flag recommendations surfaced in the
task form and on the project page.

## What works

- **Usage-metadata contract.** `AgentResult` (`packages/agent-adapter-spec`) gained
  `input_tokens`/`output_tokens`/`total_cost_usd`/`model`, all defaulting to `None`. The plan calls
  this type "AdapterResult"; the codebase's existing name (`AgentResult`, unchanged since Phase 7)
  was kept rather than a repo-wide rename with no functional benefit.
- **Adapter capture, grounded in verified schemas only.** `ClaudeCodeAdapter` parses the documented
  stream-json `result` event's `usage`/`total_cost_usd` and the leading `system`/`init` event's
  `model`. `CodexCliAdapter` parses `turn.completed`'s `usage.input_tokens`/`output_tokens` (real
  recorded fixture; codex's usage never carries a dollar amount, so `total_cost_usd` stays `None`
  there by design). `CustomShellAdapter` stays "No AI" (always null). `OpenCodeAdapter` and
  `OpenHandsAdapter` do **not** fabricate usage capture: neither adapter's verified event schema
  (constructed from provider docs/SDK models, not a live install) includes a usage/cost field, so
  both intentionally leave the new fields `None` — see Stubbed/deferred.
- **Propagation, no new communication path.** `talos-runner-supervisor`'s
  `dataclasses.asdict(result)` already forwards new `AgentResult` fields with zero code changes.
  `RunPipeline` reads them off the `"result"` event and passes them to `ApiClient.update_status()`
  (new optional `input_tokens`/`output_tokens`/`cost_usd`/`cost_model` kwargs) alongside the
  existing `exit_code`, at the `RUNNING_AGENT` → `RUNNING_TESTS` transition.
- **Persistence and policy enforcement.** `V006__cost_tracking.sql` adds nullable
  `input_tokens`/`output_tokens`/`cost_usd`/`cost_model` to `agent_runs` plus an index on
  `(project_id, agent_key, completed_at)` for aggregation. `RunService.updateStatus()` is the one
  place every status update flows through, so it's also the one place `costUsd` is forced `null`
  for `provider_auth_mode=subscription_local` runs, *even if the adapter reported a value* (Claude
  Code's CLI computes a notional `total_cost_usd` regardless of auth mode) — Section 13's "never
  estimate a price for subscription usage" is enforced centrally, not trusted to every adapter.
- **Cost aggregation.** `GET /api/v1/projects/{id}/costs/monthly` (new `dev.talos.costs`) returns
  monthly per-agent totals via a native aggregate query; a month/agent bucket with no priced runs
  reports `totalCostUsd: null` rather than a fabricated zero, and `subscriptionRunCount` is
  reported alongside so the UI can label subscription-only activity.
- **Recommendations.** `GET /api/v1/projects/{id}/recommendations` (new `dev.talos.recommendations`)
  computes, from the project's terminal run history: a `suggestedAgentKey` (highest success rate,
  ties broken toward more history), a `cheapestCapableAgentKey` (lowest average cost among agents
  with ≥50% success), per-agent stats, and risk flags — file areas (top-level path segment) with
  ≥2 runs whose `git_changes.risk_flagged` was set by the Section 12.1 policy scan. Nothing here is
  auto-applied: the dashboard only ever *displays* these as hints.
- **Task-level agent assignment made functional.** `Task.assignedAgentKey` existed since early
  phases but was PATCH-only and never consulted by `start-run`. It's now settable at task-creation
  time too (`CreateTaskRequest.assignedAgentKey`) and wired into `RunService.resolveAgentKey()` as
  a fallback tier between an explicit `StartRunRequest.agentKey` and the project's
  `talos.yaml agents.preferred` — the mechanism that makes "apply this recommendation" in the task
  form an actual operator decision rather than inert UI.
- **Dashboard.** `apps/web`'s project-detail page (the closest existing per-project data page —
  see Documented deviations) gained a monthly cost table and a recommendations panel (suggested
  agent, cheapest-capable agent, per-agent stats, risk flags). The task-creation dialog gained an
  agent-key selector plus a "Suggested agent: X — Use suggestion" hint that fills the selector on
  click; run-detail shows a run's own token/cost usage, with a "subscription usage (cost not
  tracked)" label when the run is priced-but-null for that reason.
- **Contracts.** `packages/contracts/openapi.yaml` is at `0.14.0`: `Run` gained the four usage/cost
  fields, `CreateTaskRequest` gained `assignedAgentKey`, and the two new endpoints plus
  `MonthlyCostSummary`/`AgentStat`/`RiskFlag`/`RecommendationResponse` schemas were added. The
  Angular client was regenerated (`costs.service.ts`, `recommendations.service.ts`, new models).

## Documented deviations

1. **New `agent_runs` columns, not a `run_costs` table.** The plan left this an open choice
   ("decided at phase start"). Cost/usage is 1:1 with a run (one adapter execution per run, no
   multi-turn cost breakdown in scope), so four nullable columns plus one aggregation index cover
   the requirement without a join for the common case (`GET /runs/{id}`) and with the same
   `applyPipelineDetails`-style nullable-merge pattern every other pipeline-reported field uses.
2. **OpenCode and OpenHands usage capture is deferred, not guessed.** Both adapters' verified event
   schemas (Phase 12 Track A: OpenCode's fixture is constructed from provider source/models,
   OpenHands' from the SDK's event models — neither from a live install) carry no usage/cost field.
   Rather than inventing plausible-looking field names for a wire contract with no verified
   evidence, both adapters leave the new `AgentResult` fields `None`. This is exactly the
   "run with no usage metadata degrades gracefully" acceptance criterion, not a gap in behavior —
   it's a gap in coverage, tracked below.
3. **Risk-flag signal reuses the Section 12.1 automated policy-scan outcome
   (`git_changes.risk_flagged`), not a human reviewer decision.** No structural link between
   "file area" and a human review outcome exists anywhere in the schema; the automated per-file
   risk flag is the only per-file signal that does, and "this file area has failed review twice"
   maps onto it directly without inventing new tracking.
4. **No dedicated "Command Center" page exists yet** (confirmed absent from `apps/web`'s route
   table before this phase — the closest analogues are the Kanban board and per-project/per-run
   detail pages). The recommendations panel and cost widget were added to the existing
   project-detail page rather than inventing a new page Phase 14 doesn't otherwise require.

## Stubbed / deferred

- OpenCode/OpenHands usage/cost capture, pending a verified live-install event schema for either
  provider (see deviation 2).
- Recommendation thresholds (50% minimum success rate for "cheapest capable") are a fixed
  provisional heuristic, not operator-configurable.
- No historical month-over-month chart — the cost widget is a flat per-agent/per-month table.

## Verification

- `packages/agent-adapter-spec`: `sg docker -c 'UV_CACHE_DIR=/tmp/uv-cache uv run pytest -q'` — 75
  passed, including new usage-normalization tests for Claude Code (init-event model, result-event
  usage/cost, graceful-degradation-with-no-usage) and Codex CLI (recorded-fixture usage onto
  `AgentResult`, no fabricated cost).
- `apps/orchestrator`: `UV_CACHE_DIR=/tmp/uv-cache uv run pytest -q` — 32 passed, including new
  pipeline tests asserting `agent_result` usage metadata propagates to the `RUNNING_TESTS` status
  call and degrades to nulls when absent.
- `apps/runner-supervisor`: `UV_CACHE_DIR=/tmp/uv-cache uv run pytest -q` — 24 passed (no code
  changes needed; `dataclasses.asdict` already forwards new `AgentResult` fields).
- `apps/api`: full suite —
  `sg docker -c 'cd /home/paulb/Personal/Talos/apps/api && GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon test'`
  — BUILD SUCCESSFUL, zero regressions. New coverage: `dev.talos.costs.CostServiceIntegrationTest`
  (monthly per-provider totals match known fixture usage exactly; subscription runs counted
  separately with no fabricated cost), `dev.talos.recommendations.RecommendationServiceIntegrationTest`
  (suggested-agent/cheapest-capable selection against seeded history; risk flags require ≥2
  occurrences and ignore a single one; graceful degradation with no run history),
  `RunControllerIntegrationTest` additions (usage/cost persist and appear on the run response;
  subscription_local runs never persist a reported cost even when one is sent; `start-run` falls
  back to `task.assignedAgentKey` before `talos.yaml agents.preferred`).
- `packages/contracts/openapi.yaml`: parsed with Python/YAML — version `0.14.0`; both new paths and
  all four new schemas present.
- `apps/web`: `npm run generate:api` succeeded (Node 22.23.1 via the direct nvm binary, since the
  shell's Homebrew Node 24 fails Angular's version gate — same workaround as Phase 13). `npm run
  build` and `npx ng test --watch=false` both succeeded (14 existing tests, no regressions; no new
  frontend spec files were added, consistent with this repo's existing frontend test depth).
- PDF sync: `bash docs/src/build-pdf.sh` succeeded; `md5sum` confirms
  `docs/src/talos-implementation-plan.pdf` matches `docs/Talos_Implementation_Plan.pdf`. The Phase
  14 heading in `docs/src/talos-implementation-plan.md` dropped `(provisional)`, matching how
  Phase 12/13 were closed out.
- `git diff --check` passed after stripping trailing whitespace/blank-line noise emitted by the
  generated Angular client (the same generator quirk noted in the Phase 13 report).
- Source-scoped naming guard returned no matches.
- Not checked: a live round-trip against a real Claude Code/Codex CLI billing account to confirm
  the exact dollar figures Anthropic/OpenAI would report — the adapter-level tests validate parsing
  against the documented schema and a real recorded fixture, not live billing reconciliation.
