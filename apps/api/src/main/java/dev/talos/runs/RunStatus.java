// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs;

/** Section 8.2 run state machine. */
public enum RunStatus {
	CREATED, QUEUED, PREPARING_WORKSPACE, RUNNING_AGENT, RUNNING_TESTS,
	REVIEWING, WAITING_APPROVAL, APPROVED, REJECTED, COMPLETED, FAILED, CANCELLED
}
