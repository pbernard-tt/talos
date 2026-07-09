# Phase 7 report — First real agent adapter: ClaudeCodeAdapter

## What works

- `ClaudeCodeAdapter` is the second registered, real adapter. It invokes Claude Code 2.1.197 in
  documented non-interactive stream-JSON mode with `-p`, `--verbose`, `--max-turns 50`, and
  `--permission-mode acceptEdits`; it deliberately does not use the unsafe
  `--dangerously-skip-permissions` mode.
- The runner executes Claude in the isolated workspace with `HOME` and `CLAUDE_CONFIG_DIR` rooted
  at `/var/talos/provider-homes/claude-code`. API-key and subscription-local modes are supported;
  the latter was authenticated successfully in the isolated provider home during this phase.
- Stream output becomes `TOOL_USE` and `LOG` adapter events, injected environment values are
  masked, transcripts are written only under the provider home, and `stop()`/timeout kill the full
  subprocess group. Native Claude deny settings prevent commit, push, destructive reset, and
  recursive removal as a provider-side enforcement point.
- The orchestrator assembles and persists Section 7.3's four ordered prompt sections for real
  coding adapters. Every configured context document is truncated to 8,000 characters and paths
  outside the workspace are ignored. `CustomShellAdapter` retains its documented literal-command
  exception for the deterministic Phase 6 smoke flow.
- The runner image contains Claude Code, Temurin JDK 21, and Gradle 9.5.1, matching the
  repository's Kotlin-DSL wrapper. Maven is not installed.
- Live acceptance passed: run `019f491c-15cb-7668-9c35-2e0c7ddc6186` ran an authenticated Claude
  task against a fresh Spring Boot 4.1/Gradle Kotlin DSL fixture, completed its Gradle test step,
  reached `WAITING_APPROVAL`, and produced `HelloController.java` plus
  `HelloControllerTest.java`. Workspace inspection confirmed `GET /hello` returns exactly
  `Hello, Talos!`.

## Stubbed / deferred

- `OpenCodeAdapter`, `CodexCliAdapter`, `OpenHandsAdapter`, and `GeminiCliAdapter` remain
  `NotImplementedError` stubs, as mandated by the adapter order.
- Review policy scanning, approval creation/action, serving diff artifacts, push/PR, and deployment
  remain Phase 8+ work. This run's `WAITING_APPROVAL` state does not push or commit anything.

## Verification

- `packages/agent-adapter-spec`: `UV_CACHE_DIR=/tmp/talos-uv-cache uv run pytest` — 15/15 passed:
  the six Section 7.2 contract checks against both real adapters, recorded stream-JSON parser tests,
  and the package test.
- `apps/orchestrator`: `UV_CACHE_DIR=/tmp/talos-uv-cache uv run pytest` — 18/18 passed, including
  prompt order/truncation/traversal protection and audit persistence.
- `bash -n scripts/phase7-live-smoke.sh` and `git diff --check` passed.
- Runner image rebuilt and verified live: OpenJDK 21.0.11, Gradle 9.5.1, and Claude Code 2.1.197.
- The live acceptance initially exposed two fixture-only issues—an outdated Spring Boot 3
  `WebMvcTest` import and unignored Gradle build outputs. The fixture now explicitly uses the
  Spring Boot 4 import and ignores `.gradle/`/`build/`; the accepted run itself was manually
  verified after its test step completed.
