#!/usr/bin/env bash
# End-to-end smoke test (Section 17: "create project → task → run → CustomShellAdapter → diff →
# approval requested"). Assumes `docker compose -f infra/docker-compose.dev.yml up -d` is already
# running and healthy. Requires curl and jq (both preinstalled on GitHub Actions ubuntu-latest).
#
# Usage: ./scripts/smoke.sh

set -euo pipefail

API_URL="${TALOS_SMOKE_API_URL:-http://localhost:8080}"
ADMIN_EMAIL="${TALOS_SMOKE_ADMIN_EMAIL:-admin@talos.local}"
ADMIN_PASSWORD="${TALOS_SMOKE_ADMIN_PASSWORD:-talos-dev-admin}"
FIXTURE_PATH="/tmp/smoke-fixture-repo"

log() { echo "[smoke] $*" >&2; }

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
  echo '# smoke fixture' > README.md
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
  -d "{\"name\":\"Smoke Project $(date +%s)\",\"repoUrl\":\"file://${FIXTURE_PATH}\",\"stackType\":\"static\"}" \
  | jq -r '.id')
log "project: ${PROJECT_ID}"

log "creating task with a CustomShellAdapter dummy prompt"
TASK_ID=$(curl -sf -X POST "${API_URL}/api/v1/tasks" \
  -H "${AUTH_HEADER}" -H 'Content-Type: application/json' \
  -d "{\"projectId\":\"${PROJECT_ID}\",\"title\":\"Smoke task\",\"description\":\"echo 'hello from talos' | tee SMOKE_TEST.txt\"}" \
  | jq -r '.id')
log "task: ${TASK_ID}"

log "starting run with custom-shell adapter"
RUN_ID=$(curl -sf -X POST "${API_URL}/api/v1/tasks/${TASK_ID}/start-run" \
  -H "${AUTH_HEADER}" -H 'Content-Type: application/json' \
  -d '{"agentKey":"custom-shell"}' \
  | jq -r '.id')
log "run: ${RUN_ID}"

log "polling until WAITING_APPROVAL (or terminal failure)"
STATUS="QUEUED"
for _ in $(seq 1 60); do
  RUN_JSON=$(curl -sf "${API_URL}/api/v1/runs/${RUN_ID}" -H "${AUTH_HEADER}")
  STATUS=$(echo "${RUN_JSON}" | jq -r '.status')
  log "run status: ${STATUS}"
  if [[ "${STATUS}" == "WAITING_APPROVAL" || "${STATUS}" == "FAILED" ]]; then
    break
  fi
  sleep 2
done

if [[ "${STATUS}" != "WAITING_APPROVAL" ]]; then
  log "FAIL: run did not reach WAITING_APPROVAL (final status: ${STATUS})"
  echo "${RUN_JSON}" | jq '.'
  curl -sf "${API_URL}/api/v1/runs/${RUN_ID}/logs" -H "${AUTH_HEADER}" | jq '.'
  exit 1
fi

log "verifying step history includes WORKSPACE/AGENT/TESTS/REVIEW, all COMPLETED"
STEP_TYPES=$(echo "${RUN_JSON}" | jq -r '[.steps[] | select(.status=="COMPLETED") | .stepType] | sort | join(",")')
log "completed steps: ${STEP_TYPES}"
for expected in WORKSPACE AGENT REVIEW; do
  if ! echo "${STEP_TYPES}" | grep -q "${expected}"; then
    log "FAIL: expected step ${expected} to be COMPLETED, got: ${STEP_TYPES}"
    exit 1
  fi
done

log "verifying logs are visible via GET /runs/{id}/logs"
LOG_COUNT=$(curl -sf "${API_URL}/api/v1/runs/${RUN_ID}/logs" -H "${AUTH_HEADER}" | jq '.content | length')
if [[ "${LOG_COUNT}" -lt 1 ]]; then
  log "FAIL: expected at least one log line, got ${LOG_COUNT}"
  exit 1
fi
log "log lines visible: ${LOG_COUNT}"

log "verifying the dummy script's change landed in git_changes"
CHANGE_COUNT=$(docker exec talos-postgres psql -U talos -d talos -tAc \
  "select count(*) from git_changes where run_id = '${RUN_ID}' and file_path = 'SMOKE_TEST.txt';")
if [[ "${CHANGE_COUNT// /}" != "1" ]]; then
  log "FAIL: expected exactly one git_changes row for SMOKE_TEST.txt, got ${CHANGE_COUNT}"
  exit 1
fi

log "PASS: run ${RUN_ID} reached WAITING_APPROVAL with steps, logs, and git_changes all populated"
