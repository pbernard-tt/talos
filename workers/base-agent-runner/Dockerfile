# Base image for per-run agent execution (Phase 11, Section 8/12.1): non-root, no Docker socket
# (never mounted into any per-run container -- see packages/agent-adapter-spec/container.py),
# git + the Claude Code CLI (used across every stack, not stack-specific).
#
# Build: docker build -f workers/base-agent-runner/Dockerfile -t workers/base-agent-runner .
# Build this image FIRST -- java-runner/node-runner/python-runner all extend it by local tag.
#
# Debian bookworm (not Ubuntu): confirmed compatible with the Claude Code apt repo below (this is
# the same base talos-runner-supervisor's own Dockerfile used successfully through Phase 10).
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates git curl gnupg \
    && install -d -m 0755 /etc/apt/keyrings \
    && curl -fsSL https://downloads.claude.ai/keys/claude-code.asc -o /etc/apt/keyrings/claude-code.asc \
    && echo "deb [signed-by=/etc/apt/keyrings/claude-code.asc] https://downloads.claude.ai/claude-code/apt/stable stable main" > /etc/apt/sources.list.d/claude-code.list \
    && apt-get update && apt-get install -y --no-install-recommends claude-code \
    && rm -rf /var/lib/apt/lists/* \
    # Fixed uid/gid (not --system, whose id is unpredictable) so files written by
    # talos-runner-supervisor's own `talos` user into the shared workspaces/provider-homes volumes
    # remain writable by this image's `talos` user too -- see docs/security-model.md.
    && groupadd --gid 1000 talos \
    && useradd --uid 1000 --gid 1000 --create-home --shell /bin/bash talos

USER talos
WORKDIR /workspace
