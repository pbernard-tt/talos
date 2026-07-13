#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

AGPL_SHA256="0d96a4ff68ad6d4b6f1f30f713b18d5184912ba8dd389f86aa7710db079abcb0"
APACHE_SHA256="cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"
COPYRIGHT_LINE="SPDX-FileCopyrightText: 2026 Vulkan Technologies"
errors=0
checked=0

error() {
  printf 'license-check: ERROR: %s\n' "$*" >&2
  errors=$((errors + 1))
}

is_excluded_path() {
  case "$1" in
    */.venv/*|*/node_modules/*|*/dist/*|*/build/*|*/target/*|*/coverage/*|*/vendor/*|*/vendored/*)
      return 0
      ;;
    apps/web/src/app/api/*)
      # OpenAPI Generator output is covered by its directory LICENSE and package metadata.
      return 0
      ;;
    apps/api/gradlew|apps/api/gradlew.bat|apps/api/gradle/wrapper/*)
      # Third-party Gradle wrapper files retain their upstream notices.
      return 0
      ;;
  esac
  return 1
}

expected_identifier() {
  local path="$1"

  if is_excluded_path "$path"; then
    return 1
  fi

  case "$path" in
    packages/agent-adapter-spec/*.toml|packages/agent-adapter-spec/src/*.py|packages/agent-adapter-spec/src/**/*.py|packages/agent-adapter-spec/tests/*.py|packages/agent-adapter-spec/tests/**/*.py)
      printf '%s\n' 'Apache-2.0'
      return 0
      ;;
    packages/contracts/*.yaml|packages/contracts/*.yml)
      printf '%s\n' 'Apache-2.0'
      return 0
      ;;
  esac

  case "$path" in
    .github/workflows/*.yaml|.github/workflows/*.yml|apps/*/Dockerfile|apps/**/*.java|apps/**/*.kt|apps/**/*.kts|apps/**/*.py|apps/**/*.ts|apps/**/*.html|apps/**/*.scss|apps/**/*.css|apps/**/*.sql|apps/**/*.sh|apps/**/*.yaml|apps/**/*.yml|apps/**/*.toml|apps/**/*.conf|docs/src/*.sh|infra/**/*.yaml|infra/**/*.yml|infra/**/*.sh|scripts/*.sh|workers/*/Dockerfile|workers/**/*.sh)
      printf '%s\n' 'AGPL-3.0-or-later'
      return 0
      ;;
  esac

  return 1
}

mapfile -t repository_files < <(git ls-files --cached --others --exclude-standard | sort -u)

for path in "${repository_files[@]}"; do
  [[ -f "$path" ]] || continue
  if identifier="$(expected_identifier "$path")"; then
    checked=$((checked + 1))
    if ! grep -Fq "SPDX-License-Identifier: $identifier" "$path"; then
      error "$path is first-party source but is missing 'SPDX-License-Identifier: $identifier'."
    fi
    if ! grep -Fq "$COPYRIGHT_LINE" "$path"; then
      error "$path is first-party source but is missing '$COPYRIGHT_LINE'."
    fi
  fi
done

allowed_licence_file() {
  case "$1" in
    LICENSE|apps/web/src/app/api/LICENSE|packages/agent-adapter-spec/LICENSE|packages/contracts/LICENSE|packages/project-config-schema/LICENSE)
      return 0
      ;;
  esac
  return 1
}

for path in "${repository_files[@]}"; do
  [[ -f "$path" ]] || continue
  if is_excluded_path "$path"; then
    continue
  fi
  basename="${path##*/}"
  case "$basename" in
    LICENSE|LICENSE.*|LICENCE|LICENCE.*|COPYING|COPYING.*)
      if ! allowed_licence_file "$path"; then
        error "$path is an unexpected licence file. Add an explicit boundary to scripts/check-licenses.sh after legal review, or remove it."
      fi
      ;;
  esac
done

check_sha256() {
  local path="$1"
  local expected="$2"
  local actual
  if [[ ! -f "$path" ]]; then
    error "$path is required but missing."
    return
  fi
  actual="$(sha256sum "$path" | awk '{print $1}')"
  if [[ "$actual" != "$expected" ]]; then
    error "$path does not match the approved official licence text (expected SHA-256 $expected, got $actual)."
  fi
}

check_sha256 LICENSE "$AGPL_SHA256"
for path in \
  apps/web/src/app/api/LICENSE \
  packages/agent-adapter-spec/LICENSE \
  packages/contracts/LICENSE \
  packages/project-config-schema/LICENSE; do
  check_sha256 "$path" "$APACHE_SHA256"
done

check_metadata() {
  local path="$1"
  local pattern="$2"
  local description="$3"
  if ! grep -Fq "$pattern" "$path"; then
    error "$path must declare $description."
  fi
}

check_metadata apps/web/package.json '"license": "AGPL-3.0-or-later"' 'AGPL-3.0-or-later'
check_metadata apps/web/src/app/api/package.json '"license": "Apache-2.0"' 'Apache-2.0'
check_metadata packages/agent-adapter-spec/pyproject.toml 'license = "Apache-2.0"' 'Apache-2.0'
for path in apps/orchestrator/pyproject.toml apps/runner-supervisor/pyproject.toml apps/telegram-adapter/pyproject.toml apps/whatsapp-adapter/pyproject.toml; do
  check_metadata "$path" 'license = "AGPL-3.0-or-later"' 'AGPL-3.0-or-later'
done

if ((errors > 0)); then
  printf 'license-check: FAILED with %d error(s); checked %d first-party source files.\n' "$errors" "$checked" >&2
  exit 1
fi

printf 'license-check: OK; checked %d first-party source files and all approved licence boundaries.\n' "$checked"
