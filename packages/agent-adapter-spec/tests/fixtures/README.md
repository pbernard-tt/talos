# Recorded adapter stream fixtures (Phase 12 Track A)

Per-adapter recordings of each provider CLI's non-interactive event stream. The fixture-drift
parser tests (`tests/test_codex_cli.py`, `tests/test_opencode.py`) replay every line through the
adapter's parser -- if a provider changes its stream format, re-record the fixture and the tests
show exactly where the mapping drifted.

| Fixture | Provenance |
|---|---|
| `codex_exec_stream.jsonl` | **Real recording**, 2026-07-10: `codex exec --json --skip-git-repo-check --ephemeral -s read-only "Run the command \`echo talos-fixture-probe\` and then reply with exactly: done"` against codex-cli 0.144.0. |
| `opencode_run_stream.jsonl` | **Constructed**, 2026-07-10, from the `opencode run --format json` emit code and part schemas in the provider's own repository (release v1.17.17) -- no authenticated OpenCode install was available. Re-record from a live `opencode run` when one is. |
| `openhands_events.json` | **Constructed**, 2026-07-10, from the OpenHands Software Agent SDK event models (v1.x, kind-discriminated). Re-record from a live agent-server's `GET /conversations/{id}/events` when one is deployed. |
