# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

# Stack image for project.type: python (Section 14). Build workers/base-agent-runner first.
# Build: docker build -f workers/python-runner/Dockerfile -t workers/python-runner .
FROM workers/base-agent-runner:latest
LABEL org.opencontainers.image.title="Talos Python Runner" \
      org.opencontainers.image.vendor="Vulkan Technologies" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      org.opencontainers.image.source="https://github.com/pbernard-tt/talos"

USER root
COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /usr/local/bin/

USER talos
ENV PATH="/home/talos/.local/bin:${PATH}"
# `uv python install` fetches a standalone Python 3.12 build directly rather than relying on
# whatever version Debian bookworm's own apt repos happen to ship -- avoids a repeat of the
# glibc/version-matching problem the base image's own header comment already flags for Java. Runs
# as `talos` (not root) so the interpreter lands under this user's own uv-managed install dir.
RUN uv python install 3.12 \
    && uv python pin --global 3.12 \
    && ln -sf "$(uv python find)" /home/talos/.local/bin/python3

WORKDIR /workspace
