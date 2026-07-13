// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.dashboard;

import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Review gap #11 / Section 11: surfaces the talos.dlq depth for the Command Center DLQ alert. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

	private static final String DLQ_QUEUE = "talos.dlq";

	private final RabbitAdmin rabbitAdmin;

	public DashboardController(RabbitAdmin rabbitAdmin) {
		this.rabbitAdmin = rabbitAdmin;
	}

	/** Passive lookup -- the orchestrator owns declaring/binding talos.dlq, so on a fresh install
	 * before the orchestrator has connected this reads as zero rather than erroring. */
	@GetMapping("/dlq-depth")
	public DlqDepthResponse dlqDepth() {
		QueueInformation info = rabbitAdmin.getQueueInfo(DLQ_QUEUE);
		return new DlqDepthResponse(info != null ? info.getMessageCount() : 0L);
	}
}
