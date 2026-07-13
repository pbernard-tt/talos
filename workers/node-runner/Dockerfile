# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

# Stack image for project.type: angular/node (Section 14). Build workers/base-agent-runner first.
# Build: docker build -f workers/node-runner/Dockerfile -t workers/node-runner .
FROM workers/base-agent-runner:latest
LABEL org.opencontainers.image.title="Talos Node Runner" \
      org.opencontainers.image.vendor="Vulkan Technologies" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      org.opencontainers.image.source="https://github.com/pbernard-tt/talos"

USER root
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

USER talos
WORKDIR /workspace
