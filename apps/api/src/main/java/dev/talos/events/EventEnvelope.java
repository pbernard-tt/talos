package dev.talos.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/** The Section 11 envelope every talos.events message uses; field names are snake_case on the wire. */
public record EventEnvelope<T>(
		@JsonProperty("event_id") UUID eventId,
		@JsonProperty("event_type") String eventType,
		@JsonProperty("occurred_at") Instant occurredAt,
		int version,
		T payload) {
}
