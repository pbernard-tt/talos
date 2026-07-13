# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

# Build context is this directory (no shared package dependency, unlike orchestrator/runner-supervisor):
#   docker build -f apps/telegram-adapter/Dockerfile -t talos-telegram-adapter apps/telegram-adapter
FROM python:3.12-slim
LABEL org.opencontainers.image.title="Talos Telegram Adapter" \
      org.opencontainers.image.vendor="Vulkan Technologies" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      org.opencontainers.image.source="https://github.com/pbernard-tt/talos"
COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /usr/local/bin/

RUN useradd --system --create-home talos
WORKDIR /app

COPY . /app
RUN uv sync --frozen --no-dev

USER talos
ENTRYPOINT ["uv", "run", "--no-sync", "talos-telegram-adapter"]
