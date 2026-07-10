# Build context is this directory (no shared package dependency, unlike orchestrator/runner-supervisor):
#   docker build -f apps/telegram-adapter/Dockerfile -t talos-telegram-adapter apps/telegram-adapter
FROM python:3.12-slim
COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /usr/local/bin/

RUN useradd --system --create-home talos
WORKDIR /app

COPY . /app
RUN uv sync --frozen --no-dev

USER talos
ENTRYPOINT ["uv", "run", "--no-sync", "talos-telegram-adapter"]
