# Base image for per-run agent execution (Phase 11, Section 8/12.1): non-root, no Docker socket
# (never mounted into any per-run container -- see packages/agent-adapter-spec/container.py),
# git + the agent CLIs (used across every stack, not stack-specific): Claude Code (Phase 7),
# OpenCode and Codex (Phase 12 Track A -- versions pinned to what the adapters verified; the
# adapters' pre-launch capability check probes `<cli> --version` in this image).
#
# Build: docker build -f workers/base-agent-runner/Dockerfile -t workers/base-agent-runner .
# Build this image FIRST -- java-runner/node-runner/python-runner all extend it by local tag.
#
# Debian bookworm (not Ubuntu): confirmed compatible with the Claude Code apt repo below (this is
# the same base talos-runner-supervisor's own Dockerfile used successfully through Phase 10).
FROM debian:bookworm-slim

# Phase 12 Track A adapter CLIs -- keep in lockstep with min_cli_version in
# packages/agent-adapter-spec/{opencode,codex_cli}.py.
ARG OPENCODE_VERSION=1.17.17
ARG CODEX_VERSION=0.144.0

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates git curl gnupg \
    && install -d -m 0755 /etc/apt/keyrings \
    && curl -fsSL https://downloads.claude.ai/keys/claude-code.asc -o /etc/apt/keyrings/claude-code.asc \
    && echo "deb [signed-by=/etc/apt/keyrings/claude-code.asc] https://downloads.claude.ai/claude-code/apt/stable stable main" > /etc/apt/sources.list.d/claude-code.list \
    && apt-get update && apt-get install -y --no-install-recommends claude-code \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL "https://github.com/anomalyco/opencode/releases/download/v${OPENCODE_VERSION}/opencode-linux-x64.tar.gz" \
        | tar -xz -C /usr/local/bin opencode \
    && curl -fsSL "https://github.com/openai/codex/releases/download/rust-v${CODEX_VERSION}/codex-x86_64-unknown-linux-musl.tar.gz" \
        | tar -xz -C /usr/local/bin \
    && mv /usr/local/bin/codex-x86_64-unknown-linux-musl /usr/local/bin/codex \
    # codex's file-editing tool spawns a sibling `codex-code-mode-host` binary, shipped as a
    # separate release asset -- without it every codex edit fails at runtime.
    && curl -fsSL "https://github.com/openai/codex/releases/download/rust-v${CODEX_VERSION}/codex-code-mode-host-x86_64-unknown-linux-musl.tar.gz" \
        | tar -xz -C /usr/local/bin \
    && mv /usr/local/bin/codex-code-mode-host-x86_64-unknown-linux-musl /usr/local/bin/codex-code-mode-host \
    && chmod 755 /usr/local/bin/opencode /usr/local/bin/codex /usr/local/bin/codex-code-mode-host \
    # Fixed uid/gid (not --system, whose id is unpredictable) so files written by
    # talos-runner-supervisor's own `talos` user into the shared workspaces/provider-homes volumes
    # remain writable by this image's `talos` user too -- see docs/security-model.md.
    && groupadd --gid 1000 talos \
    && useradd --uid 1000 --gid 1000 --create-home --shell /bin/bash talos

USER talos
WORKDIR /workspace
