// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs;

import dev.talos.runs.dto.LogEntryResponse;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Section 10.3's SSE bridge. The API is both publisher and subscriber of Redis channel
 * {@code talos:run:{id}:logs} -- publishing over Redis rather than delivering in-process keeps
 * this correct if talos-api is ever horizontally scaled (Section 11: "Redis holds... log
 * pub/sub"), even though in the MVP's single-instance deployment the publish and the open SSE
 * connection are typically the same JVM.
 */
@Component
public class RunEventBroadcaster {

	private final StringRedisTemplate redisTemplate;
	private final RedisMessageListenerContainer listenerContainer;
	private final ObjectMapper objectMapper;
	private final AgentRunLogRepository agentRunLogRepository;

	private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByRun = new ConcurrentHashMap<>();

	public RunEventBroadcaster(StringRedisTemplate redisTemplate, RedisMessageListenerContainer listenerContainer,
			ObjectMapper objectMapper, AgentRunLogRepository agentRunLogRepository) {
		this.redisTemplate = redisTemplate;
		this.listenerContainer = listenerContainer;
		this.objectMapper = objectMapper;
		this.agentRunLogRepository = agentRunLogRepository;
	}

	private static String channel(UUID runId) {
		return "talos:run:" + runId + ":logs";
	}

	public void publishLog(UUID runId, LogEntryResponse entry) {
		publish(runId, "log", entry);
	}

	public void publishStatus(UUID runId, String from, String to) {
		publish(runId, "status", Map.of("from", from, "to", to));
	}

	public void publishStep(UUID runId, String stepType, String status) {
		publish(runId, "step", Map.of("stepType", stepType, "status", status));
	}

	private void publish(UUID runId, String type, Object data) {
		String json = objectMapper.writeValueAsString(Map.of("type", type, "data", data));
		redisTemplate.convertAndSend(channel(runId), json);
	}

	/** Backfills from Postgres (durable record), then relays live Redis messages until the client disconnects. */
	public SseEmitter subscribe(UUID runId, long afterSequence) {
		SseEmitter emitter = new SseEmitter(0L);

		List<AgentRunLog> backlog = agentRunLogRepository
				.findByRunIdAndSequenceGreaterThanOrderBySequenceAsc(runId, afterSequence);
		for (AgentRunLog log : backlog) {
			sendLogEvent(emitter, LogEntryResponse.from(log));
		}

		MessageListener listener = (message, pattern) -> relay(emitter, message);
		ChannelTopic topic = new ChannelTopic(channel(runId));
		listenerContainer.addMessageListener(listener, topic);
		emittersByRun.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>()).add(emitter);

		Runnable cleanup = () -> {
			listenerContainer.removeMessageListener(listener, topic);
			CopyOnWriteArrayList<SseEmitter> list = emittersByRun.get(runId);
			if (list != null) {
				list.remove(emitter);
			}
		};
		emitter.onCompletion(cleanup);
		emitter.onTimeout(cleanup);
		emitter.onError(ex -> cleanup.run());

		return emitter;
	}

	private void relay(SseEmitter emitter, Message message) {
		try {
			String body = new String(message.getBody(), StandardCharsets.UTF_8);
			JsonNode node = objectMapper.readTree(body);
			String type = node.get("type").asString();
			JsonNode data = node.get("data");
			SseEmitter.SseEventBuilder event = SseEmitter.event().name(type).data(data);
			if ("log".equals(type) && data.get("sequence") != null) {
				event = event.id(data.get("sequence").asString());
			}
			emitter.send(event);
		} catch (IOException | RuntimeException e) {
			emitter.completeWithError(e);
		}
	}

	private void sendLogEvent(SseEmitter emitter, LogEntryResponse entry) {
		try {
			emitter.send(SseEmitter.event().id(String.valueOf(entry.sequence())).name("log").data(entry));
		} catch (IOException e) {
			emitter.completeWithError(e);
		}
	}

	/** Section 10.3: "Heartbeat comment every 15 s." */
	@Scheduled(fixedRate = 15000)
	void heartbeat() {
		emittersByRun.values().forEach(list -> list.forEach(emitter -> {
			try {
				emitter.send(SseEmitter.event().comment("heartbeat"));
			} catch (IOException e) {
				emitter.completeWithError(e);
			}
		}));
	}
}
