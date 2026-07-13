// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.tasks;

import java.util.Map;
import java.util.Set;

/**
 * Legal manual (human-drag / move-endpoint) transitions. Section 8.2's "Task <-> run status
 * mapping" assigns RUNNING/REVIEW/DONE/BLOCKED to the run state machine exclusively (set only by
 * the API as a side effect of an agent run's own transitions, wired up in Phase 5) — so a human
 * can only manually move a task among BACKLOG, READY, and CANCELLED. CANCELLED is terminal
 * (matches "cancelled tasks... never a column" in Section 6.2). Reordering within the same
 * column (status unchanged) is always legal.
 */
public final class TaskTransitionValidator {

	private static final Map<TaskStatus, Set<TaskStatus>> LEGAL_TARGETS = Map.of(
			TaskStatus.BACKLOG, Set.of(TaskStatus.READY, TaskStatus.CANCELLED),
			TaskStatus.READY, Set.of(TaskStatus.BACKLOG, TaskStatus.CANCELLED));

	private TaskTransitionValidator() {
	}

	public static boolean isLegal(TaskStatus from, TaskStatus to) {
		if (from == to) {
			return true;
		}
		return LEGAL_TARGETS.getOrDefault(from, Set.of()).contains(to);
	}
}
