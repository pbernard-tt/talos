// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.events;

import dev.talos.common.UuidV7;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Publishes to the talos.events topic exchange (Section 11); routing key == event type. */
@Component
public class EventPublisher {

	private final RabbitTemplate rabbitTemplate;

	public EventPublisher(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void publish(String eventType, Object payload) {
		EventEnvelope<Object> envelope = new EventEnvelope<>(UuidV7.generate(), eventType, Instant.now(), 1, payload);
		rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, eventType, envelope);
	}
}
