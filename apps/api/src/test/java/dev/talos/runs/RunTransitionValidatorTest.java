// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Exhaustive RunStatus x RunStatus matrix against Section 8.2's state machine, verbatim. */
class RunTransitionValidatorTest {

	private static final Map<RunStatus, Set<RunStatus>> LEGAL = Map.ofEntries(
			Map.entry(RunStatus.CREATED, Set.of(RunStatus.QUEUED, RunStatus.FAILED)),
			Map.entry(RunStatus.QUEUED, Set.of(RunStatus.PREPARING_WORKSPACE, RunStatus.FAILED, RunStatus.CANCELLED)),
			Map.entry(RunStatus.PREPARING_WORKSPACE,
					Set.of(RunStatus.RUNNING_AGENT, RunStatus.FAILED, RunStatus.CANCELLED)),
			Map.entry(RunStatus.RUNNING_AGENT,
					Set.of(RunStatus.RUNNING_TESTS, RunStatus.FAILED, RunStatus.CANCELLED)),
			Map.entry(RunStatus.RUNNING_TESTS, Set.of(RunStatus.REVIEWING, RunStatus.FAILED, RunStatus.CANCELLED)),
			Map.entry(RunStatus.REVIEWING,
					Set.of(RunStatus.WAITING_APPROVAL, RunStatus.FAILED, RunStatus.CANCELLED)),
			Map.entry(RunStatus.WAITING_APPROVAL, Set.of(RunStatus.APPROVED, RunStatus.REJECTED, RunStatus.FAILED,
					RunStatus.CANCELLED)),
			Map.entry(RunStatus.APPROVED, Set.of(RunStatus.COMPLETED, RunStatus.FAILED)),
			Map.entry(RunStatus.COMPLETED, Set.of()),
			Map.entry(RunStatus.FAILED, Set.of()),
			Map.entry(RunStatus.CANCELLED, Set.of()),
			Map.entry(RunStatus.REJECTED, Set.of()));

	@ParameterizedTest
	@EnumSource(RunStatus.class)
	void everyTransitionOutOfEachStatus_matchesTheExpectedMatrix(RunStatus from) {
		for (RunStatus to : RunStatus.values()) {
			boolean expected = LEGAL.get(from).contains(to);
			assertThat(RunTransitionValidator.isLegal(from, to))
					.as("%s -> %s".formatted(from, to))
					.isEqualTo(expected);
		}
	}

	@Test
	void happyPath_isFullyLegal() {
		assertThat(RunTransitionValidator.isLegal(RunStatus.CREATED, RunStatus.QUEUED)).isTrue();
		assertThat(RunTransitionValidator.isLegal(RunStatus.QUEUED, RunStatus.PREPARING_WORKSPACE)).isTrue();
		assertThat(RunTransitionValidator.isLegal(RunStatus.PREPARING_WORKSPACE, RunStatus.RUNNING_AGENT)).isTrue();
		assertThat(RunTransitionValidator.isLegal(RunStatus.RUNNING_AGENT, RunStatus.RUNNING_TESTS)).isTrue();
		assertThat(RunTransitionValidator.isLegal(RunStatus.RUNNING_TESTS, RunStatus.REVIEWING)).isTrue();
		assertThat(RunTransitionValidator.isLegal(RunStatus.REVIEWING, RunStatus.WAITING_APPROVAL)).isTrue();
		assertThat(RunTransitionValidator.isLegal(RunStatus.WAITING_APPROVAL, RunStatus.APPROVED)).isTrue();
		assertThat(RunTransitionValidator.isLegal(RunStatus.APPROVED, RunStatus.COMPLETED)).isTrue();
	}

	@Test
	void skippingStates_isIllegal() {
		assertThat(RunTransitionValidator.isLegal(RunStatus.QUEUED, RunStatus.RUNNING_AGENT)).isFalse();
		assertThat(RunTransitionValidator.isLegal(RunStatus.CREATED, RunStatus.COMPLETED)).isFalse();
	}

	@Test
	void terminalStates_haveNoOutgoingTransitions() {
		for (RunStatus terminal : Set.of(RunStatus.COMPLETED, RunStatus.FAILED, RunStatus.CANCELLED,
				RunStatus.REJECTED)) {
			for (RunStatus to : RunStatus.values()) {
				assertThat(RunTransitionValidator.isLegal(terminal, to)).as("%s -> %s".formatted(terminal, to))
						.isFalse();
			}
		}
	}

	@Test
	void cancelIsOnlyLegalFromQueuedThroughWaitingApproval_notFromApproved() {
		assertThat(RunTransitionValidator.isLegal(RunStatus.APPROVED, RunStatus.CANCELLED)).isFalse();
		assertThat(RunTransitionValidator.isLegal(RunStatus.WAITING_APPROVAL, RunStatus.CANCELLED)).isTrue();
	}

	@Test
	void timeouts_matchSection8Point2() {
		assertThat(RunTransitionValidator.timeoutFor(RunStatus.QUEUED)).isEqualTo(Duration.ofMinutes(10));
		assertThat(RunTransitionValidator.timeoutFor(RunStatus.PREPARING_WORKSPACE)).isEqualTo(Duration.ofMinutes(5));
		assertThat(RunTransitionValidator.timeoutFor(RunStatus.RUNNING_AGENT)).isEqualTo(Duration.ofMinutes(30));
		assertThat(RunTransitionValidator.timeoutFor(RunStatus.RUNNING_TESTS)).isEqualTo(Duration.ofMinutes(20));
		assertThat(RunTransitionValidator.timeoutFor(RunStatus.REVIEWING)).isEqualTo(Duration.ofMinutes(5));
		assertThat(RunTransitionValidator.timeoutFor(RunStatus.APPROVED)).isEqualTo(Duration.ofMinutes(10));
		assertThat(RunTransitionValidator.timeoutFor(RunStatus.WAITING_APPROVAL)).isNull();
	}
}
