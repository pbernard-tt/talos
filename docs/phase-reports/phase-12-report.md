# Phase 12 report — Additional adapters and remote triggers

Two independent tracks, gated together (Section 16). Both are complete; this closes the Phase 12 gate.

## What works

### Track A — adapters (`OpenCodeAdapter`, `CodexCliAdapter`, `OpenHandsAdapter`)

Landed in the two commits preceding this report ("Phase 12 Track A: OpenCode, Codex CLI, and
OpenHands adapters + pre-launch capability check" and "worker-image agent CLIs, per-adapter smoke
script, docs"). Summary (see `docs/initial_implementation_log.md` for the full entry): all three
adapters replace their Phase 6 `NotImplementedError` stubs, each preceded by verification of current
CLI/API flags against provider documentation and recording the verified version in its module
docstring; a shared capability check (`cli_agent.py`) fails a run with a clear `SYSTEM` log line
before ever spawning the agent if the CLI/credentials aren't present; the Section 7.2 contract suite
passes for every adapter against recorded fixtures; the Phase 6 fixture-repo smoke run reaches
`WAITING_APPROVAL` with each new adapter with only `assigned_agent_key` changed and zero
orchestrator/runner code modified (proven live for `codex-cli` with real subscription credentials).

### Track B — remote triggers (Telegram first, then WhatsApp)

- **Integration-scoped service accounts, ahead of Phase 15's general RBAC matrix.** The plan's
  "least-privilege: task create/read only" requirement for the chat trigger JWTs had no enforcement
  mechanism to hang it on — `SecurityConfig` runs pure owner-mode today (every authenticated JWT
  request passes every check; Role exists in the schema but isn't checked). Resolved by asking
  rather than guessing (per CLAUDE.md): built a small, purpose-specific guard instead of either
  inventing the Phase 15 matrix early or silently giving the bots full access.
  `JwtService.issue()` stamps an `integration_scoped` claim on tokens for the two seeded service
  accounts (`IntegrationServiceAccountSeeder`, mirroring `AdminSeeder`, from
  `TALOS_{TELEGRAM,WHATSAPP}_SERVICE_EMAIL`/`_PASSWORD`); `IntegrationScopeFilter` enforces a hard
  allow-list for any token carrying that claim — `GET`/`POST /api/v1/tasks(/*)`, `GET
  /api/v1/projects(/*)`, `GET /api/v1/runs/*`, `GET /api/v1/approvals(/*)`, `POST
  /api/v1/chat/rejected-sender` — and 403s plus writes an `integration.access_denied` audit row for
  anything else (verified live: the seeded Telegram account can list/create tasks but a start-run or
  `GET /api/v1/integrations` call both 403; the admin account is completely unaffected). The
  `/internal/v1` half of the requirement was already free — it's a separate filter chain requiring
  the service token, unreachable by any REST JWT regardless of scope.
- **`tasks.source`** (already `DASHBOARD|WEBHOOK|TELEGRAM|...` per the Section 9 schema comment, but
  never previously settable) is now an optional field on `CreateTaskRequest`, validated against a
  known set and falling back to `DASHBOARD` otherwise.
- **`POST /api/v1/chat/rejected-sender`**, the one small necessary REST-surface addition: talos-api
  is the sole PostgreSQL writer, so a chat adapter (no DB connection, only a REST client role) has no
  other way to satisfy "a message from any other chat ID produces ... an audit row." It records
  provenance only (channel + chat ID), never the disallowed message's contents.
- **`apps/telegram-adapter`** — long-polls the Bot API (chosen over webhooks: no public HTTPS
  endpoint needed for a self-hosted single-VPS deployment); a chat-ID allow-list gates every inbound
  message before any API call; four slash commands (`/create_task <project> :: <title> ::
  [description]`, `/task_status <id>`, `/run_status <id>`, `/approvals`) deterministically parsed —
  no NLU/LLM layer, per hard constraint 1; a `talos.events` notifier on its own quorum queue
  (`talos.notifiers.telegram`), idempotent on `event_id` via Redis exactly like the orchestrator's
  consumers, formats `approval.requested`/`pr.created`/`run.status.changed` into a chat message with
  a dashboard deep link (`/review/{run_id}` or `/runs/{run_id}`). Idles cleanly (logs a warning, no
  crash-loop) when `TALOS_TELEGRAM_BOT_TOKEN`/`_ALLOWED_CHAT_IDS` are unset, since Track B is
  optional post-MVP and `docker compose up` must still work without it configured.
- **`apps/whatsapp-adapter`** — the second implementation of the identical command schema
  (`packages/contracts/chat/inbound-command.schema.json`) via the WhatsApp Business Cloud API.
  Unlike Telegram, the Cloud API only supports webhooks, so this is a small FastAPI service instead
  of a poll loop: `GET /webhook` handles Meta's verification handshake; `POST /webhook` verifies
  `X-Hub-Signature-256` over the raw body against the app secret before anything else runs, then
  applies the same allow-list/command/notifier logic. Serves `/health` only (no notifier) when
  unconfigured, same idle-safe reasoning as Telegram.
- **No approval, reject, deploy, or push command exists in either adapter's grammar** — the
  acceptance criterion "no approval can be granted from chat" is true by construction, not by a
  runtime check that could be bypassed.

## Documented deviations

1. **The integration-scope allow-list is a bespoke filter, not the Section 16 Phase 15 RBAC
   matrix.** It only distinguishes "integration-scoped" from "everything else" (a boolean), not the
   full `OWNER`/`MAINTAINER`/`REVIEWER`/`VIEWER` matrix. This was an explicit choice (confirmed via
   `AskUserQuestion` rather than guessed): building the general matrix now would be scope creep ahead
   of Phase 15; leaving the bots fully unrestricted would violate the plan's explicit "least
   privilege" acceptance criterion. `IntegrationScopeFilter`'s allow-list should be revisited once
   Phase 15 lands — at that point it likely collapses into an ordinary role check.
2. **Chat notifications broadcast to every allow-listed chat ID/WhatsApp ID**, not to the specific
   operator who requested the run. Multi-user run ownership doesn't exist before Phase 15 either
   (Section 12.2: MVP is single-admin, owner-mode), so there's no "the right person" to target yet —
   broadcasting to the whole (currently: personal-use, single-operator) allow-list is the correct
   MVP behavior, not a shortcut.
3. **Telegram uses long-polling, WhatsApp uses webhooks** — not a deviation so much as a
   platform-forced asymmetry: the plan's Track B text explicitly leaves the choice open ("webhook or
   long-poll") for Telegram, and the WhatsApp Cloud API has no long-poll option at all. Documented
   here since it means the two adapters have different runtime shapes (a bare asyncio loop vs. a
   FastAPI+uvicorn service) despite sharing the same command schema and REST/notifier logic.
4. **No live acceptance test against a real Telegram bot or WhatsApp Business account.** Neither
   platform's credentials were available in this environment. What *was* verified live: the seeded
   service-account JWTs against the real running `talos-api` (login, scope enforcement, 403 on
   disallowed endpoints), both adapter Docker images built and run in the full dev compose stack,
   the idle-safe behavior when unconfigured, and the WhatsApp webhook's signature verification and
   Meta handshake logic via FastAPI's `TestClient`. The Telegram Bot API long-poll loop and the
   WhatsApp Cloud API's outbound message-send call are covered by unit tests against fake HTTP
   clients, not a live provider round-trip — flagged for re-verification before first production use,
   same spirit as the Track A adapter fixtures' provenance notes.

## Stubbed / deferred

- Real Phase 15 RBAC (deviation 1) — `IntegrationScopeFilter` is the interim mechanism.
- Per-user notification targeting (deviation 2) — depends on Phase 15 multi-user support.
- `GeminiCliAdapter` remains the only adapter stub (unchanged, backlog per Section 7.4 #6).

## Verification

- `apps/api`: `sg docker -c "./gradlew test"` — full suite green (127 tests across all modules,
  including the new `IntegrationScopeFilterIntegrationTest` covering allow/deny/audit-row behavior
  and the admin-unaffected case), zero regressions.
- `apps/telegram-adapter`: `uv run pytest` — 31 passed (command parsing against the shared JSON
  Schema via `jsonschema.validate`, handler behavior against a fake API client, notifier
  idempotency/fan-out, allow-list gating and rejected-sender recording via `poll_once`, the
  `is_configured` idle guard).
- `apps/whatsapp-adapter`: `uv run pytest` — 39 passed (same command-parsing/handler/notifier
  coverage against the WhatsApp payload shape, `verify_signature` HMAC correctness including
  tampered-body and wrong-secret rejection, and `TestClient`-driven FastAPI route tests for the
  verification handshake and signature-gated webhook).
- Naming guard (scoped form) returns nothing.
- `docker compose -f infra/docker-compose.dev.yml config --quiet` passes with both new services
  added; a full `docker compose up -d --build` brought up all 8 services (including
  `talos-telegram-adapter`/`talos-whatsapp-adapter`) healthy/running from a clean image rebuild.
- **Live, against the real running stack:**
  - `POST /api/v1/auth/login` with each seeded service account returns a JWT whose decoded payload
    carries `"integration_scoped":true`.
  - With that JWT: `GET /api/v1/tasks` → 200; `GET /api/v1/integrations` → 403
    `INTEGRATION_SCOPE_FORBIDDEN`.
  - `talos-telegram-adapter` with no bot token configured stays `Up` indefinitely, logging the idle
    warning once, no restart loop.
  - `talos-whatsapp-adapter` with no Cloud API credentials configured reports healthy on `/health`
    and correctly 403s the verification handshake (no verify token configured to match against).
  - `./scripts/smoke.sh` (baseline `CustomShellAdapter` flow) re-run after all Phase 12 changes:
    PASS, no regression.
- **Not checked:** live round-trip against a real Telegram bot token or WhatsApp Business Cloud API
  account (deviation 4) — both adapters' provider-facing HTTP calls are covered by unit tests against
  fakes/signature math only, not a live external API.

## CI

`.github/workflows/ci.yml`: `python` matrix gained `apps/telegram-adapter`/`apps/whatsapp-adapter`;
`docker-build` and `container-scan` gained both new images (built from their own directories, unlike
`talos-orchestrator`/`talos-runner-supervisor`, since neither adapter depends on
`packages/agent-adapter-spec`); the `smoke` job's existing unscoped `docker compose up -d --build`
picks up both new services automatically since they're now declared in
`infra/docker-compose.dev.yml`.
