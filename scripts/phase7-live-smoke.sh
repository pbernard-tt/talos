#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

# Phase 7 live acceptance (Section 16): authenticated Claude Code changes a fixture Spring Boot
# project, Gradle runs the resulting tests, and the Talos pipeline reaches WAITING_APPROVAL.
#
# Run as root when the current shell cannot access /var/run/docker.sock:
#   sudo bash scripts/phase7-live-smoke.sh

set -euo pipefail

API_URL="${TALOS_SMOKE_API_URL:-http://localhost:8080}"
ADMIN_EMAIL="${TALOS_SMOKE_ADMIN_EMAIL:-admin@talos.local}"
ADMIN_PASSWORD="${TALOS_SMOKE_ADMIN_PASSWORD:-talos-dev-admin}"
FIXTURE_PATH="/tmp/phase7-hello-fixture"
RUNNER_CONTAINER="talos-runner-supervisor"

log() { echo "[phase7-live-smoke] $*" >&2; }
fail() { log "FAIL: $*"; exit 1; }

# Avoid a host jq dependency: the runner image already contains Python.  Input is JSON on stdin;
# the argument is a dotted object path (the live API responses used here have only object fields).
json_field() {
  docker exec -i "${RUNNER_CONTAINER}" python -c '
import json
import sys

value = json.load(sys.stdin)
for part in sys.argv[1].split("."):
    value = value[part]
print(value if value is not None else "null")
' "$1"
}

for _ in $(seq 1 30); do
  curl -sf "${API_URL}/actuator/health" >/dev/null 2>&1 && break
  sleep 2
done
curl -sf "${API_URL}/actuator/health" >/dev/null || fail "talos-api did not become healthy"

log "creating a fresh Gradle Kotlin DSL Spring Boot fixture in the runner"
docker exec -i "${RUNNER_CONTAINER}" sh -s -- "${FIXTURE_PATH}" <<'EOF'
set -eu
fixture_path="$1"
rm -rf "$fixture_path"
mkdir -p "$fixture_path/src/main/java/com/example/fixture"
cd "$fixture_path"

cat > settings.gradle.kts <<'FILE'
rootProject.name = "phase7-hello-fixture"
FILE

cat > build.gradle.kts <<'FILE'
plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
FILE

cat > src/main/java/com/example/fixture/FixtureApplication.java <<'FILE'
package com.example.fixture;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class FixtureApplication {
    public static void main(String[] args) {
        SpringApplication.run(FixtureApplication.class, args);
    }
}

@RestController
class StatusController {
    @GetMapping("/status")
    String status() {
        return "ok";
    }
}
FILE

cat > README.md <<'FILE'
# Phase 7 fixture

This is a minimal Spring Boot 4.1 project using Gradle Kotlin DSL. Keep the existing `/status`
endpoint. Implement the requested endpoint and a focused Spring MVC test. Spring Boot 4's MVC test
annotation is `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (not the older
`org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`). Do not commit changes.
FILE

cat > .gitignore <<'FILE'
.gradle/
build/
FILE

cat > talos.yaml <<'FILE'
project:
  name: Phase 7 hello fixture
  type: spring-boot
  repo: .
  default_branch: main
commands:
  test: gradle test --no-daemon
agents:
  preferred: claude-code
  allowed: [claude-code]
rules:
  forbidden: [commit_secrets, modify_env_files, force_push_main]
context:
  docs: [README.md]
FILE

git init --initial-branch=main -q
git config user.email phase7@talos.local
git config user.name 'Talos Phase 7 Fixture'
git add .
git commit -q -m 'initial Spring Boot fixture'
EOF

log "logging in as the Talos admin"
TOKEN=$(curl -sf -X POST "${API_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}" | json_field token)
[[ -n "${TOKEN}" && "${TOKEN}" != "null" ]] || fail "admin login did not return a token"
AUTH_HEADER="Authorization: Bearer ${TOKEN}"

PROJECT_ID=$(curl -sf -X POST "${API_URL}/api/v1/projects" \
  -H "${AUTH_HEADER}" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Phase 7 Hello $(date +%s)\",\"repoUrl\":\"file://${FIXTURE_PATH}\",\"stackType\":\"spring-boot\"}" | json_field id)
[[ -n "${PROJECT_ID}" && "${PROJECT_ID}" != "null" ]] || fail "project creation did not return an ID"

CONFIG_JSON=$(docker exec -i "${RUNNER_CONTAINER}" python -c '
import json
import sys
print(json.dumps({"configYaml": sys.stdin.read()}))
' < <(docker exec "${RUNNER_CONTAINER}" cat "${FIXTURE_PATH}/talos.yaml"))
curl -sf -X POST "${API_URL}/api/v1/projects/${PROJECT_ID}/sync-config" \
  -H "${AUTH_HEADER}" -H 'Content-Type: application/json' -d "${CONFIG_JSON}" >/dev/null

TASK_ID=$(curl -sf -X POST "${API_URL}/api/v1/tasks" \
  -H "${AUTH_HEADER}" -H 'Content-Type: application/json' \
  -d "{\"projectId\":\"${PROJECT_ID}\",\"title\":\"Add a hello endpoint\",\"description\":\"Add GET /hello that returns exactly Hello, Talos!. Add a focused Spring MVC test at src/test/java/com/example/fixture/HelloControllerTest.java. This is Spring Boot 4.1: use org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest. Keep /status unchanged.\"}" | json_field id)
[[ -n "${TASK_ID}" && "${TASK_ID}" != "null" ]] || fail "task creation did not return an ID"

RUN_ID=$(curl -sf -X POST "${API_URL}/api/v1/tasks/${TASK_ID}/start-run" \
  -H "${AUTH_HEADER}" -H 'Content-Type: application/json' \
  -d '{"agentKey":"claude-code","authMode":"subscription_local"}' | json_field id)
[[ -n "${RUN_ID}" && "${RUN_ID}" != "null" ]] || fail "run start did not return an ID"
log "run: ${RUN_ID}"

STATUS="QUEUED"
for _ in $(seq 1 240); do
  RUN_JSON=$(curl -sf "${API_URL}/api/v1/runs/${RUN_ID}" -H "${AUTH_HEADER}")
  STATUS=$(printf '%s' "${RUN_JSON}" | json_field status)
  log "run status: ${STATUS}"
  case "${STATUS}" in
    WAITING_APPROVAL) break ;;
    FAILED|CANCELLED) break ;;
  esac
  sleep 5
done

if [[ "${STATUS}" != "WAITING_APPROVAL" ]]; then
  curl -sf "${API_URL}/api/v1/runs/${RUN_ID}/logs" -H "${AUTH_HEADER}" || true
  fail "run did not reach WAITING_APPROVAL (final status: ${STATUS})"
fi

COMPLETED_STEPS=$(printf '%s' "${RUN_JSON}" | docker exec -i "${RUNNER_CONTAINER}" python -c '
import json
import sys

run = json.load(sys.stdin)
print(",".join(sorted(step["stepType"] for step in run["steps"] if step["status"] == "COMPLETED")))
')
for step in WORKSPACE AGENT TESTS REVIEW; do
  grep -q "${step}" <<<"${COMPLETED_STEPS}" || fail "${step} was not completed (${COMPLETED_STEPS})"
done

MAIN_CHANGE_COUNT=$(docker exec talos-postgres psql -U talos -d talos -tAc \
  "select count(*) from git_changes where run_id = '${RUN_ID}' and file_path like 'src/main/java/com/example/fixture/%.java';")
TEST_CHANGE_COUNT=$(docker exec talos-postgres psql -U talos -d talos -tAc \
  "select count(*) from git_changes where run_id = '${RUN_ID}' and file_path = 'src/test/java/com/example/fixture/HelloControllerTest.java';")
[[ "${MAIN_CHANGE_COUNT// /}" -ge 1 ]] || fail "expected an agent change under src/main/java/com/example/fixture"
[[ "${TEST_CHANGE_COUNT// /}" == "1" ]] || fail "expected the focused HelloControllerTest change"

WORKSPACE_PATH=$(printf '%s' "${RUN_JSON}" | json_field workspacePath)
docker exec "${RUNNER_CONTAINER}" grep -R -F '@GetMapping("/hello")' "${WORKSPACE_PATH}/src/main/java" >/dev/null \
  || fail "the agent-authored source does not declare GET /hello"
docker exec "${RUNNER_CONTAINER}" grep -R -F 'Hello, Talos!' "${WORKSPACE_PATH}/src/main/java" >/dev/null \
  || fail "the agent-authored source does not return Hello, Talos!"

log "PASS: Claude authored the /hello endpoint and focused test; Gradle tests passed; run ${RUN_ID} is WAITING_APPROVAL"
