-- SPDX-FileCopyrightText: 2026 Vulkan Technologies
-- SPDX-License-Identifier: AGPL-3.0-or-later

-- Phase 8 (Review and approval flow): durable storage for the unified diff text and per-file
-- matched policy pattern, both needed to serve the Review Center (see docs/phase-reports/phase-8-report.md).
ALTER TABLE agent_runs ADD COLUMN diff_patch TEXT;
ALTER TABLE git_changes ADD COLUMN matched_pattern VARCHAR(200);
