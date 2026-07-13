# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

# Build context must be the repository root, e.g.:
#   docker build -f apps/runner-supervisor/Dockerfile -t talos-runner-supervisor .
#
# Phase 11: agent execution moved into per-run `workers/*` containers (see workers/), so this image
# no longer needs Java/Gradle/Claude Code -- it only needs to launch and supervise those containers.
# It gains the Docker CLI instead (talos-runner-supervisor is the one container that mounts
# /var/run/docker.sock; the containers it spawns never do). Stripping the agent toolchains out of
# this image is a real hardening win since it's the privileged, docker.sock-holding one.
FROM python:3.12-slim
LABEL org.opencontainers.image.title="Talos Runner Supervisor" \
      org.opencontainers.image.vendor="Vulkan Technologies" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      org.opencontainers.image.source="https://github.com/pbernard-tt/talos"
COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /usr/local/bin/

RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates git curl docker-cli \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 1000 talos \
    && useradd --uid 1000 --gid 1000 --create-home talos

WORKDIR /app
COPY packages/agent-adapter-spec /app/packages/agent-adapter-spec
COPY apps/runner-supervisor /app/apps/runner-supervisor

WORKDIR /app/apps/runner-supervisor
RUN uv sync --frozen --no-dev

# /var/talos/{workspaces,provider-homes} are mounted as named volumes at runtime; chown here so
# their first-mount content-copy (Docker seeds a fresh named volume from the image path) is owned
# by the non-root user this container runs as.
RUN mkdir -p /var/talos/workspaces /var/talos/provider-homes && chown -R talos:talos /var/talos

USER talos
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=15s CMD curl -f http://localhost:8081/health || exit 1
ENTRYPOINT ["uv", "run", "--no-sync", "talos-runner-supervisor"]
