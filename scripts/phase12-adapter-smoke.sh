#!/usr/bin/env bash
# Phase 12 Track A acceptance (Section 16): the Phase 6 fixture-repo smoke flow reaches
# WAITING_APPROVAL with a *real* second/third adapter, with only the agent key (and its auth mode)
# changed relative to scripts/smoke.sh -- zero orchestrator/runner code differences.
#
# Prerequisites beyond smoke.sh's: the selected adapter's credentials must already be in its
# provider home inside the talos_provider_homes volume, e.g. for codex-cli:
#   docker exec talos-runner-supervisor mkdir -p /var/talos/provider-homes/codex-cli/.codex
#   docker cp ~/.codex/auth.json talos-runner-supervisor:/var/talos/provider-homes/codex-cli/.codex/
# A missing credential fails the run with a SYSTEM "capability check failed" log line -- this
# script prints the run logs on failure so that line is visible.
#
# Usage: TALOS_SMOKE_AGENT_KEY=codex-cli TALOS_SMOKE_AUTH_MODE=subscription_local ./scripts/phase12-adapter-smoke.sh

set -euo pipefail

API_URL="${TALOS_SMOKE_API_URL:-http://localhost:8080}"
ADMIN_EMAIL="${TALOS_SMOKE_ADMIN_EMAIL:-admin@talos.local}"
ADMIN_PASSWORD="${TALOS_SMOKE_ADMIN_PASSWORD:-talos-dev-admin}"
AGENT_KEY="${TALOS_SMOKE_AGENT_KEY:-codex-cli}"
AUTH_MODE="${TALOS_SMOKE_AUTH_MODE:-api_key}"
FIXTURE_PATH="/tmp/phase12-smoke-fixture-repo-${AGENT_KEY}"

log() { echo "[phase12-smoke:${AGENT_KEY}] $*" >&2; }

log "waiting for talos-api at ${API_URL} ..."
for _ in $(seq 1 30); do
  if curl -sf "${API_URL}/actuator/health" > /dev/null 2>&1; then
    break
  fi
  sleep 2
done

log "creating fixture git repo inside talos-runner-supervisor at ${FIXTURE_PATH}"
docker exec talos-runner-supervisor sh -c "
  set -e
  rm -rf '${FIXTURE_PATH}'
  mkdir -p '${FIXTURE_PATH}'
  cd '${FIXTURE_PATH}'
  git init --initial-branch=main -q
  git config user.email smoke@talos.local
  git config user.name 'Talos Smoke'
  echo '# phase 12 smoke fixture' > README.md
  git add -A
  git commit -q -m 'initial commit'
"

log "logging in as admin"
TOKEN=$(curl -sf -X POST "${API_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}" | jq -r '.token')

AUTH_HEADER="Authorization: Bearer ${TOKEN}"

log "creating project pointing at the fixture repo"
PROJECT_ID=$(curl -sf -X POST "${API_URL}/api/v1/projects" \
  -H "${AUTH_HEADER}" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Phase 12 Smoke ${AGENT_KEY} $(date +%s)\",\"repoUrl\":\"file://${FIXTURE_PATH}\",\"stackType\":\"static\"}" \
  | jq -r '.id')
log "project: ${PROJECT_ID}"

log "creating task (a real coding-agent prompt, unlike smoke.sh's custom-shell command)"
TASK_ID=$(curl -sf -X POST "${API_URL}/api/v1/tasks" \
  -H "${AUTH_HEADER}" -H 'Content-Type: application/json' \
  -d "{\"projectId\":\"${PROJECT_ID}\",\"title\":\"Phase 12 smoke task\",\"description\":\"Create a file named SMOKE_TEST.txt at the repository root containing exactly this single line: hello from talos\"}" \
  | jq -r '.id')
log "task: ${TASK_ID}"

log "starting run with agentKey=${AGENT_KEY} authMode=${AUTH_MODE}"
RUN_ID=$(curl -sf -X POST "${API_URL}/api/v1/tasks/${TASK_ID}/start-run" \
  -H "${AUTH_HEADER}" -H 'Content-Type: application/json' \
  -d "{\"agentKey\":\"${AGENT_KEY}\",\"authMode\":\"${AUTH_MODE}\"}" \
  | jq -r '.id')
log "run: ${RUN_ID}"

log "polling until WAITING_APPROVAL (or terminal failure)"
STATUS="QUEUED"
for _ in $(seq 1 240); do
  RUN_JSON=$(curl -sf "${API_URL}/api/v1/runs/${RUN_ID}" -H "${AUTH_HEADER}")
  STATUS=$(echo "${RUN_JSON}" | jq -r '.status')
  log "run status: ${STATUS}"
  if [[ "${STATUS}" == "WAITING_APPROVAL" || "${STATUS}" == "FAILED" || "${STATUS}" == "CANCELLED" ]]; then
    break
  fi
  sleep 5
done

if [[ "${STATUS}" != "WAITING_APPROVAL" ]]; then
  log "FAIL: run did not reach WAITING_APPROVAL (final status: ${STATUS})"
  echo "${RUN_JSON}" | jq '.'
  curl -sf "${API_URL}/api/v1/runs/${RUN_ID}/logs" -H "${AUTH_HEADER}" | jq '.'
  exit 1
fi

log "verifying step history includes WORKSPACE/AGENT/REVIEW, all COMPLETED"
STEP_TYPES=$(echo "${RUN_JSON}" | jq -r '[.steps[] | select(.status=="COMPLETED") | .stepType] | sort | join(",")')
log "completed steps: ${STEP_TYPES}"
for expected in WORKSPACE AGENT REVIEW; do
  if ! echo "${STEP_TYPES}" | grep -q "${expected}"; then
    log "FAIL: expected step ${expected} to be COMPLETED, got: ${STEP_TYPES}"
    exit 1
  fi
done

log "verifying the agent's change landed in git_changes"
CHANGE_COUNT=$(docker exec talos-postgres psql -U talos -d talos -tAc \
  "select count(*) from git_changes where run_id = '${RUN_ID}' and file_path = 'SMOKE_TEST.txt';")
if [[ "${CHANGE_COUNT// /}" != "1" ]]; then
  log "FAIL: expected exactly one git_changes row for SMOKE_TEST.txt, got ${CHANGE_COUNT}"
  exit 1
fi

log "PASS: run ${RUN_ID} reached WAITING_APPROVAL via ${AGENT_KEY} with only the agent key changed"
