-- SPDX-FileCopyrightText: 2026 Vulkan Technologies
-- SPDX-License-Identifier: AGPL-3.0-or-later

-- Phase 14: per-run cost tracking (Section 16). Usage/cost columns are nullable -- adapters that
-- can't report them (CustomShellAdapter; OpenCode/OpenHands pending a verified usage event schema)
-- leave a row with nulls instead of failing the pipeline. subscription_local runs must never get a
-- fabricated dollar cost (Section 13) -- enforced in RunService, not here.
ALTER TABLE agent_runs
  ADD COLUMN input_tokens INT,
  ADD COLUMN output_tokens INT,
  ADD COLUMN cost_usd NUMERIC(10,4),
  ADD COLUMN cost_model VARCHAR(100);

-- Serves the monthly per-provider/per-project cost aggregation query (dashboard cost widget).
CREATE INDEX idx_runs_project_agent_completed ON agent_runs(project_id, agent_key, completed_at);
