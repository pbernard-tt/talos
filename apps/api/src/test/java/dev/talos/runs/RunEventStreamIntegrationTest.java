package dev.talos.runs;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 acceptance: "logs POSTed internally appear on an open SSE connection." Uses a real
 * running server (not MockMvc, which can't drive a live streaming response while another request
 * is posted concurrently) and a raw streaming java.net.http.HttpClient for every call.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+"
})
class RunEventStreamIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4.1-management");

	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.url",
				() -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
	}

	@LocalServerPort
	private int port;

	private final HttpClient client = HttpClient.newHttpClient();

	private String baseUrl() {
		return "http://localhost:" + port;
	}

	@Test
	void logsPostedInternally_appearOnOpenSseConnection() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId);

		HttpRequest sseRequest = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl() + "/api/v1/runs/" + runId + "/events/stream"))
				.header("Authorization", token)
				.GET()
				.build();

		HttpResponse<Stream<String>> response = client.send(sseRequest, HttpResponse.BodyHandlers.ofLines());
		assertThat(response.statusCode()).isEqualTo(200);

		List<String> lines = new CopyOnWriteArrayList<>();
		Stream<String> lineStream = response.body();
		Thread reader = startReaderThread(lineStream, lines);

		try {
			postInternalLog(runId, 1, "hello from orchestrator");

			boolean sawMessage = pollUntil(() -> lines.stream().anyMatch(l -> l.contains("hello from orchestrator")),
					Duration.ofSeconds(10));
			assertThat(sawMessage).as("log message never relayed over SSE; captured: %s", lines).isTrue();
			assertThat(lines.stream().anyMatch(l -> l.toLowerCase().contains("event") && l.toLowerCase().contains("log")))
					.as("expected a named 'log' SSE event among: %s", lines)
					.isTrue();
		} finally {
			lineStream.close();
			reader.interrupt();
		}
	}

	@Test
	void reconnecting_backfillsFromPersistedLogsAfterGivenSequence() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String taskId = createTask(token, projectId);
		String runId = startRun(token, taskId);

		postInternalLog(runId, 1, "first line");
		postInternalLog(runId, 2, "second line");

		HttpRequest sseRequest = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl() + "/api/v1/runs/" + runId + "/events/stream?afterSequence=1"))
				.header("Authorization", token)
				.GET()
				.build();
		HttpResponse<Stream<String>> response = client.send(sseRequest, HttpResponse.BodyHandlers.ofLines());

		List<String> lines = new CopyOnWriteArrayList<>();
		Stream<String> lineStream = response.body();
		Thread reader = startReaderThread(lineStream, lines);

		try {
			boolean sawSecond = pollUntil(() -> lines.stream().anyMatch(l -> l.contains("second line")),
					Duration.ofSeconds(10));
			assertThat(sawSecond).as("backfilled log after afterSequence=1 never arrived; captured: %s", lines)
					.isTrue();
			assertThat(lines.stream().anyMatch(l -> l.contains("first line")))
					.as("sequence 1 should NOT be backfilled when afterSequence=1")
					.isFalse();
		} finally {
			lineStream.close();
			reader.interrupt();
		}
	}

	private Thread startReaderThread(Stream<String> lineStream, List<String> lines) {
		Thread reader = new Thread(() -> {
			try {
				lineStream.forEach(lines::add);
			} catch (RuntimeException ignored) {
				// stream closed by the test's finally block
			}
		});
		reader.setDaemon(true);
		reader.start();
		return reader;
	}

	private boolean pollUntil(java.util.function.BooleanSupplier condition, Duration timeout)
			throws InterruptedException {
		long deadlineNanos = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadlineNanos) {
			if (condition.getAsBoolean()) {
				return true;
			}
			Thread.sleep(100);
		}
		return condition.getAsBoolean();
	}

	private void postInternalLog(String runId, long sequence, String message) throws Exception {
		String body = """
				{"entries":[{"stream":"STDOUT","sequence":%d,"message":"%s","timestamp":"2026-07-09T12:00:00Z"}]}
				""".formatted(sequence, message);
		HttpResponse<String> response = post(baseUrl() + "/internal/v1/runs/" + runId + "/logs", body,
				"X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+");
		assertThat(response.statusCode()).isEqualTo(204);
	}

	private String bearerToken() throws Exception {
		HttpResponse<String> response = post(baseUrl() + "/api/v1/auth/login",
				"{\"email\":\"admin@test.local\",\"password\":\"test-admin-password\"}", null, null);
		return "Bearer " + JsonPath.<String>read(response.body(), "$.token");
	}

	private String createProject(String token) throws Exception {
		String body = """
				{"name":"SSE Project %s","repoUrl":"git@github.com:org/sse.git","stackType":"spring-boot"}
				""".formatted(UUID.randomUUID());
		HttpResponse<String> response = post(baseUrl() + "/api/v1/projects", body, "Authorization", token);
		return JsonPath.read(response.body(), "$.id");
	}

	private String createTask(String token, String projectId) throws Exception {
		String body = "{\"projectId\":\"" + projectId + "\",\"title\":\"SSE task\"}";
		HttpResponse<String> response = post(baseUrl() + "/api/v1/tasks", body, "Authorization", token);
		return JsonPath.read(response.body(), "$.id");
	}

	private String startRun(String token, String taskId) throws Exception {
		HttpResponse<String> response = post(baseUrl() + "/api/v1/tasks/" + taskId + "/start-run",
				"{\"agentKey\":\"custom-shell\"}", "Authorization", token);
		return JsonPath.read(response.body(), "$.id");
	}

	private HttpResponse<String> post(String url, String body, String headerName, String headerValue)
			throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (headerName != null) {
			builder.header(headerName, headerValue);
		}
		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
}
