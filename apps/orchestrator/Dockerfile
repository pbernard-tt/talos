# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

# Build context must be the repository root, e.g.:
#   docker build -f apps/orchestrator/Dockerfile -t talos-orchestrator .
FROM python:3.12-slim
LABEL org.opencontainers.image.title="Talos Orchestrator" \
      org.opencontainers.image.vendor="Vulkan Technologies" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      org.opencontainers.image.source="https://github.com/pbernard-tt/talos"
COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /usr/local/bin/

RUN useradd --system --create-home talos
WORKDIR /app

COPY packages/agent-adapter-spec /app/packages/agent-adapter-spec
COPY apps/orchestrator /app/apps/orchestrator

WORKDIR /app/apps/orchestrator
RUN uv sync --frozen --no-dev

USER talos
ENTRYPOINT ["uv", "run", "--no-sync", "talos-orchestrator"]
