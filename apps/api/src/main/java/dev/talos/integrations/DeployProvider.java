// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.integrations;

/** Section 6.2/10.4's deploy trigger, behind an interface so DokployDeployProvider is swappable (Phase 10). */
public interface DeployProvider {

	boolean testConnection(String baseUrl, String token);

	void trigger(String baseUrl, String token, String appId, String title);

	/** Polls the most recent deployment for appId. Empty means still in progress (idle/running) -- not a terminal result yet. */
	DeployPollResult pollLatestStatus(String baseUrl, String token, String appId);

	record DeployPollResult(boolean terminal, DeployStatus status, String errorMessage) {

		static DeployPollResult inProgress() {
			return new DeployPollResult(false, null, null);
		}

		static DeployPollResult succeeded() {
			return new DeployPollResult(true, DeployStatus.SUCCEEDED, null);
		}

		static DeployPollResult failed(String errorMessage) {
			return new DeployPollResult(true, DeployStatus.FAILED, errorMessage);
		}
	}
}
