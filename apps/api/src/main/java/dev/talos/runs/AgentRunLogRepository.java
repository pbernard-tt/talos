// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentRunLogRepository extends JpaRepository<AgentRunLog, UUID> {
	List<AgentRunLog> findByRunIdAndSequenceGreaterThanOrderBySequenceAsc(UUID runId, long sequence);

	List<AgentRunLog> findByRunIdOrderBySequenceAsc(UUID runId);

	Page<AgentRunLog> findByRunIdAndSequenceGreaterThan(UUID runId, long sequence, Pageable pageable);
}
