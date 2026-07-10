# Stack image for project.type: angular/node (Section 14). Build workers/base-agent-runner first.
# Build: docker build -f workers/node-runner/Dockerfile -t workers/node-runner .
FROM workers/base-agent-runner:latest

USER root
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

USER talos
WORKDIR /workspace
