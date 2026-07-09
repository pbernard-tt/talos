# Build context must be the repository root, e.g.:
#   docker build -f apps/runner-supervisor/Dockerfile -t talos-runner-supervisor .
# Use Jammy explicitly: its glibc is compatible with Debian bookworm used by python:3.12-slim.
# The unqualified Temurin tag can advance to a newer base whose java launcher will not load here.
FROM eclipse-temurin:21-jdk-jammy AS java-runtime
FROM python:3.12-slim
COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /usr/local/bin/
COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

ARG GRADLE_VERSION=9.5.1

RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates git curl gnupg unzip \
    && install -d -m 0755 /etc/apt/keyrings \
    && curl -fsSL https://downloads.claude.ai/keys/claude-code.asc -o /etc/apt/keyrings/claude-code.asc \
    && echo "deb [signed-by=/etc/apt/keyrings/claude-code.asc] https://downloads.claude.ai/claude-code/apt/stable stable main" > /etc/apt/sources.list.d/claude-code.list \
    && apt-get update && apt-get install -y --no-install-recommends claude-code \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle \
    && rm /tmp/gradle.zip \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home talos

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
