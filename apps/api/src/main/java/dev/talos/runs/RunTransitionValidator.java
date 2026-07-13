// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Section 8.2's run state machine, verbatim:
 *
 * <pre>
 * CREATED -&gt; QUEUED -&gt; PREPARING_WORKSPACE -&gt; RUNNING_AGENT -&gt; RUNNING_TESTS
 *         -&gt; REVIEWING -&gt; WAITING_APPROVAL -&gt; APPROVED -&gt; COMPLETED
 *
 * Failure edge:  any non-terminal state -&gt; FAILED   (error_message required)
 * Cancel edge:   QUEUED ... WAITING_APPROVAL -&gt; CANCELLED (user action)
 * Reject edge:   WAITING_APPROVAL -&gt; REJECTED        (task returns to READY)
 * </pre>
 */
public final class RunTransitionValidator {

	private static final Set<RunStatus> TERMINAL = Set.of(RunStatus.COMPLETED, RunStatus.FAILED,
			RunStatus.CANCELLED, RunStatus.REJECTED);

	private static final Set<RunStatus> CANCELLABLE_FROM = Set.of(RunStatus.QUEUED, RunStatus.PREPARING_WORKSPACE,
			RunStatus.RUNNING_AGENT, RunStatus.RUNNING_TESTS, RunStatus.REVIEWING, RunStatus.WAITING_APPROVAL);

	private static final Map<RunStatus, Set<RunStatus>> HAPPY_PATH = Map.of(
			RunStatus.CREATED, Set.of(RunStatus.QUEUED),
			RunStatus.QUEUED, Set.of(RunStatus.PREPARING_WORKSPACE),
			RunStatus.PREPARING_WORKSPACE, Set.of(RunStatus.RUNNING_AGENT),
			RunStatus.RUNNING_AGENT, Set.of(RunStatus.RUNNING_TESTS),
			RunStatus.RUNNING_TESTS, Set.of(RunStatus.REVIEWING),
			RunStatus.REVIEWING, Set.of(RunStatus.WAITING_APPROVAL),
			RunStatus.WAITING_APPROVAL, Set.of(RunStatus.APPROVED, RunStatus.REJECTED),
			RunStatus.APPROVED, Set.of(RunStatus.COMPLETED));

	/** Section 8.2's per-transition timeout column; null means no reaper deadline for that state. */
	private static final Map<RunStatus, Duration> TIMEOUTS = Map.of(
			RunStatus.QUEUED, Duration.ofMinutes(10),
			RunStatus.PREPARING_WORKSPACE, Duration.ofMinutes(5),
			RunStatus.RUNNING_AGENT, Duration.ofMinutes(30),
			RunStatus.RUNNING_TESTS, Duration.ofMinutes(20),
			RunStatus.REVIEWING, Duration.ofMinutes(5),
			RunStatus.APPROVED, Duration.ofMinutes(10));

	private RunTransitionValidator() {
	}

	public static boolean isLegal(RunStatus from, RunStatus to) {
		if (to == RunStatus.FAILED) {
			return !TERMINAL.contains(from);
		}
		if (to == RunStatus.CANCELLED) {
			return CANCELLABLE_FROM.contains(from);
		}
		return HAPPY_PATH.getOrDefault(from, Set.of()).contains(to);
	}

	/** Null means the reaper never expires a run sitting in this state (e.g. WAITING_APPROVAL). */
	public static Duration timeoutFor(RunStatus status) {
		return TIMEOUTS.get(status);
	}

	public static boolean isTerminal(RunStatus status) {
		return TERMINAL.contains(status);
	}

	public static Set<RunStatus> terminalStatuses() {
		return TERMINAL;
	}

	/** States the reaper considers: every state with a configured timeout in TIMEOUTS. */
	public static Set<RunStatus> reapableStatuses() {
		return TIMEOUTS.keySet();
	}
}
