# Provider authentication

## Claude Code

Talos runs Claude Code with a provider-specific, Docker-volume-backed home at
`/var/talos/provider-homes/claude-code`. Credentials must be created there, never in a project
workspace, repository, container image, or Talos log.

The runner-supervisor image includes Claude Code from Anthropic's signed stable APT repository.
Rebuild it before authenticating:

```bash
sudo docker compose -f infra/docker-compose.dev.yml up -d --build runner-supervisor
```

For subscription authentication, open a shell in the runner-supervisor container and run:

```bash
HOME=/var/talos/provider-homes/claude-code \
CLAUDE_CONFIG_DIR=/var/talos/provider-homes/claude-code/.claude \
claude login
```

Complete the provider-approved login flow once. Start runs with `authMode: subscription_local`.
Talos does not expose a browser login flow or attempt to broker customer subscription credentials.

For automation, store `ANTHROPIC_API_KEY` as an approved project secret and inject it only for the
run. Start those runs with `authMode: api_key`. The runner passes the value only in the child
process environment; it is never written to `talos.yaml`, a workspace, a transcript, or an API
response, and adapter event output masks every injected value.

The adapter invokes Claude Code 2.1.206-compatible documented flags: `-p`, `--output-format
stream-json`, `--verbose`, `--max-turns 50`, and `--permission-mode acceptEdits`. It also sets
`HOME` and `CLAUDE_CONFIG_DIR` to the isolated provider home. On first use it merges provider-native
deny settings for commit, push, destructive reset, and recursive removal. Those settings are an
additional enforcement point; Talos's process/container boundary and later post-run review gate
remain authoritative.
