// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.events;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Section 11: the talos.events topic exchange. Consumer queues/bindings belong to whichever service consumes them (the orchestrator, from Phase 6). */
@Configuration
public class RabbitConfig {

	public static final String EVENTS_EXCHANGE = "talos.events";

	@Bean
	public TopicExchange talosEventsExchange() {
		return new TopicExchange(EVENTS_EXCHANGE, true, false);
	}

	@Bean
	public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
		return new JacksonJsonMessageConverter();
	}

	@Bean
	public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, JacksonJsonMessageConverter converter) {
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMessageConverter(converter);
		return template;
	}

	/** Passive queue lookups only (Command Center DLQ depth, review gap #11) -- this app never
	 * declares/binds talos.dlq itself, that stays the orchestrator's responsibility (Section 11). */
	@Bean
	public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
		return new RabbitAdmin(connectionFactory);
	}
}
