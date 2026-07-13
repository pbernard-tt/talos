// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.costs;

import java.math.BigDecimal;

/** Spring Data JPA interface projection for {@link CostQueryRepository}'s native aggregate query --
 * getter names must match the query's column aliases (case-insensitively). */
public interface MonthlyCostProjection {
	String getAgentKey();

	String getMonth();

	BigDecimal getTotalCostUsd();

	Long getTotalInputTokens();

	Long getTotalOutputTokens();

	Long getRunCount();

	Long getSubscriptionRunCount();
}
