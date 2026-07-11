#!/bin/sh
# Scheduled backup loop for the db-backup sidecar (docs/deployment.md Section 6). Same
# `pg_dump -F c` custom format validated end-to-end in the Phase 11 restore drill
# (docs/security-model.md#8-backup-and-restore-drill-executed-2026-07-10), just run on a timer
# instead of by hand. No cron daemon in the postgres image -- a plain sleep loop is simplest and
# matches how apps/orchestrator's own retention sweep is written (Section 8.3).
set -eu

BACKUP_DIR="${TALOS_DB_BACKUP_DIR:-/backups}"
RETENTION_DAYS="${TALOS_DB_BACKUP_RETENTION_DAYS:-14}"
INTERVAL_SECONDS="${TALOS_DB_BACKUP_INTERVAL_SECONDS:-86400}"

mkdir -p "$BACKUP_DIR"

while true; do
  timestamp=$(date -u +%Y%m%dT%H%M%SZ)
  dest="$BACKUP_DIR/talos-$timestamp.dump"
  echo "[pg-backup] $(date -u +%Y-%m-%dT%H:%M:%SZ) dumping to $dest"
  if pg_dump -F c -f "$dest.tmp"; then
    mv "$dest.tmp" "$dest"
    echo "[pg-backup] wrote $dest"
  else
    echo "[pg-backup] pg_dump failed, leaving previous backups untouched" >&2
    rm -f "$dest.tmp"
  fi
  find "$BACKUP_DIR" -name 'talos-*.dump' -mtime "+$RETENTION_DAYS" -delete
  sleep "$INTERVAL_SECONDS"
done
