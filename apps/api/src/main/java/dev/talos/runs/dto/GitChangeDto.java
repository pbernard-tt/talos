// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import dev.talos.runs.GitChange;
import dev.talos.runs.GitChangeType;
import jakarta.validation.constraints.NotNull;

public record GitChangeDto(@NotNull String filePath, @NotNull GitChangeType changeType, int additions,
		int deletions) {

	public static GitChangeDto from(GitChange change) {
		return new GitChangeDto(change.getFilePath(), change.getChangeType(), change.getAdditions(),
				change.getDeletions());
	}
}
