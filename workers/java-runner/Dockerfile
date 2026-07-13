# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

# Stack image for project.type: spring-boot (Section 14). Build workers/base-agent-runner first.
# Build: docker build -f workers/java-runner/Dockerfile -t workers/java-runner .
FROM eclipse-temurin:21-jdk-jammy AS java-runtime
FROM workers/base-agent-runner:latest
LABEL org.opencontainers.image.title="Talos Java Runner" \
      org.opencontainers.image.vendor="Vulkan Technologies" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      org.opencontainers.image.source="https://github.com/pbernard-tt/talos"

USER root
COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Both Gradle (verified live in Phase 7 against a Kotlin-DSL Spring Boot fixture -- see
# docs/phase-reports/phase-7-report.md) and Maven (Debian's packaged version, cheap to include)
# are installed since a Talos-managed project could use either.
ARG GRADLE_VERSION=9.5.1
RUN apt-get update && apt-get install -y --no-install-recommends unzip maven \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle \
    && rm /tmp/gradle.zip \
    && rm -rf /var/lib/apt/lists/*

USER talos
WORKDIR /workspace
