// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs.dto;

import dev.talos.runs.AgentRunLog;
import dev.talos.runs.LogStream;

import java.time.Instant;

public record LogEntryResponse(long sequence, LogStream stream, String message, Instant createdAt) {

	public static LogEntryResponse from(AgentRunLog log) {
		return new LogEntryResponse(log.getSequence(), log.getStream(), log.getMessage(), log.getCreatedAt());
	}
}
