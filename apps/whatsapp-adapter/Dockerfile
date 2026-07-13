# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

# Build context is this directory (no shared package dependency, unlike orchestrator/runner-supervisor):
#   docker build -f apps/whatsapp-adapter/Dockerfile -t talos-whatsapp-adapter apps/whatsapp-adapter
FROM python:3.12-slim
LABEL org.opencontainers.image.title="Talos WhatsApp Adapter" \
      org.opencontainers.image.vendor="Vulkan Technologies" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      org.opencontainers.image.source="https://github.com/pbernard-tt/talos"
COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /usr/local/bin/

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home talos

WORKDIR /app
COPY . /app
RUN uv sync --frozen --no-dev

USER talos
EXPOSE 8082
HEALTHCHECK --interval=15s --timeout=5s --start-period=15s CMD curl -f http://localhost:8082/health || exit 1
ENTRYPOINT ["uv", "run", "--no-sync", "talos-whatsapp-adapter"]
