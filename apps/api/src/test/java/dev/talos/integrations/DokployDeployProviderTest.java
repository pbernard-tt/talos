package dev.talos.integrations;

import com.sun.net.httpserver.HttpServer;
import dev.talos.common.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** "mock-Dokploy integration test covers success and failure" (Phase 10 plan): no live Dokploy instance. */
class DokployDeployProviderTest {

	private HttpServer server;
	private DokployDeployProvider provider;
	private String baseUrl;
	private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();
	private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.start();
		baseUrl = "http://localhost:" + server.getAddress().getPort();
		provider = new DokployDeployProvider();
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	@Test
	void testConnection_okStatus_returnsTrue() throws IOException {
		server.createContext("/api/project.all", exchange -> {
			lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
			exchange.sendResponseHeaders(200, 0);
			exchange.close();
		});

		assertThat(provider.testConnection(baseUrl, "dokploy-test-token")).isTrue();
		assertThat(lastApiKeyHeader.get()).isEqualTo("dokploy-test-token");
	}

	@Test
	void testConnection_unauthorized_returnsFalse() throws IOException {
		server.createContext("/api/project.all", exchange -> {
			exchange.sendResponseHeaders(401, 0);
			exchange.close();
		});

		assertThat(provider.testConnection(baseUrl, "bad-token")).isFalse();
	}

	@Test
	void trigger_success_sendsApplicationIdAndTitle() throws IOException {
		server.createContext("/api/application.deploy", exchange -> {
			lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
			lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});

		provider.trigger(baseUrl, "dokploy-test-token", "app-123", "talos: deploy run run1");

		assertThat(lastApiKeyHeader.get()).isEqualTo("dokploy-test-token");
		assertThat(lastRequestBody.get()).contains("\"applicationId\":\"app-123\"");
	}

	@Test
	void trigger_dokployRejects_throwsApiExceptionWithoutLeakingToken() throws IOException {
		server.createContext("/api/application.deploy", exchange -> {
			byte[] body = "{\"code\":\"BAD_REQUEST\",\"message\":\"Invalid input data\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(400, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});

		assertThatThrownBy(() -> provider.trigger(baseUrl, "dokploy-test-token", "app-123", "title"))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("400")
				.hasMessageNotContaining("dokploy-test-token");
	}

	@Test
	void pollLatestStatus_done_returnsSucceeded() throws IOException {
		server.createContext("/api/deployment.all", exchange -> {
			byte[] body = "[{\"status\":\"running\"},{\"status\":\"done\"}]".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});

		DeployProvider.DeployPollResult result = provider.pollLatestStatus(baseUrl, "token", "app-123");

		assertThat(result.terminal()).isTrue();
		assertThat(result.status()).isEqualTo(DeployStatus.SUCCEEDED);
	}

	@Test
	void pollLatestStatus_error_returnsFailedWithMessage() throws IOException {
		server.createContext("/api/deployment.all", exchange -> {
			byte[] body = "[{\"status\":\"error\",\"errorMessage\":\"build failed\"}]".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});

		DeployProvider.DeployPollResult result = provider.pollLatestStatus(baseUrl, "token", "app-123");

		assertThat(result.terminal()).isTrue();
		assertThat(result.status()).isEqualTo(DeployStatus.FAILED);
		assertThat(result.errorMessage()).isEqualTo("build failed");
	}

	@Test
	void pollLatestStatus_runningOrIdle_isNotTerminal() throws IOException {
		server.createContext("/api/deployment.all", exchange -> {
			byte[] body = "[{\"status\":\"running\"}]".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});

		assertThat(provider.pollLatestStatus(baseUrl, "token", "app-123").terminal()).isFalse();
	}

	@Test
	void pollLatestStatus_emptyList_isNotTerminal() throws IOException {
		server.createContext("/api/deployment.all", exchange -> {
			byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});

		assertThat(provider.pollLatestStatus(baseUrl, "token", "app-123").terminal()).isFalse();
	}
}
