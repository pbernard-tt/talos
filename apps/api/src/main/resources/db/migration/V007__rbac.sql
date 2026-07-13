-- SPDX-FileCopyrightText: 2026 Vulkan Technologies
-- SPDX-License-Identifier: AGPL-3.0-or-later

-- Phase 15: multi-user RBAC enforcement (Section 12.2/9.3). `role` has existed on users since
-- V001; this migration adds what enforcement itself needs: a way to deactivate a user, and a way
-- to know who requested a run (for the self-approval prohibition -- "the user who requested a run
-- cannot approve it").
ALTER TABLE users ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE agent_runs ADD COLUMN requested_by UUID REFERENCES users(id);
