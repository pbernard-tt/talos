# Build context must be the repository root, e.g.:
#   docker build -f apps/runner-supervisor/Dockerfile -t talos-runner-supervisor .
FROM python:3.12-slim
COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /usr/local/bin/

RUN apt-get update && apt-get install -y --no-install-recommends git curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home talos

WORKDIR /app
COPY packages/agent-adapter-spec /app/packages/agent-adapter-spec
COPY apps/runner-supervisor /app/apps/runner-supervisor

WORKDIR /app/apps/runner-supervisor
RUN uv sync --frozen --no-dev

USER talos
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=15s CMD curl -f http://localhost:8081/health || exit 1
ENTRYPOINT ["uv", "run", "talos-runner-supervisor"]
