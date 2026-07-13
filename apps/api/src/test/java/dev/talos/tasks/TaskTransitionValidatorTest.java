// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.tasks;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Exhaustive TaskStatus x TaskStatus matrix (Phase 4 acceptance: "Backlog->Done rejected with 422"). */
class TaskTransitionValidatorTest {

	private static final Map<TaskStatus, Set<TaskStatus>> LEGAL = Map.of(
			TaskStatus.BACKLOG, Set.of(TaskStatus.BACKLOG, TaskStatus.READY, TaskStatus.CANCELLED),
			TaskStatus.READY, Set.of(TaskStatus.READY, TaskStatus.BACKLOG, TaskStatus.CANCELLED),
			TaskStatus.RUNNING, Set.of(TaskStatus.RUNNING),
			TaskStatus.REVIEW, Set.of(TaskStatus.REVIEW),
			TaskStatus.BLOCKED, Set.of(TaskStatus.BLOCKED),
			TaskStatus.DONE, Set.of(TaskStatus.DONE),
			TaskStatus.CANCELLED, Set.of(TaskStatus.CANCELLED));

	@ParameterizedTest
	@EnumSource(TaskStatus.class)
	void everyTransitionOutOfEachStatus_matchesTheExpectedMatrix(TaskStatus from) {
		for (TaskStatus to : TaskStatus.values()) {
			boolean expected = LEGAL.get(from).contains(to);
			assertThat(TaskTransitionValidator.isLegal(from, to))
					.as("%s -> %s".formatted(from, to))
					.isEqualTo(expected);
		}
	}

	@ParameterizedTest
	@EnumSource(TaskStatus.class)
	void sameStatus_isAlwaysLegal(TaskStatus status) {
		assertThat(TaskTransitionValidator.isLegal(status, status)).isTrue();
	}

	@org.junit.jupiter.api.Test
	void backlogToDone_isIllegal() {
		assertThat(TaskTransitionValidator.isLegal(TaskStatus.BACKLOG, TaskStatus.DONE)).isFalse();
	}

	@org.junit.jupiter.api.Test
	void backlogToReady_isLegal() {
		assertThat(TaskTransitionValidator.isLegal(TaskStatus.BACKLOG, TaskStatus.READY)).isTrue();
	}
}
