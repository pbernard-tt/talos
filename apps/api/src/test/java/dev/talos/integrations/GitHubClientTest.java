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

/** "GitHub client against a mock server" (Phase 9 plan): no live network call to api.github.com. */
class GitHubClientTest {

	private HttpServer server;
	private GitHubClientImpl client;
	private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
	private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.start();
		client = new GitHubClientImpl("http://localhost:" + server.getAddress().getPort());
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	@Test
	void testConnection_okStatus_returnsTrue() throws IOException {
		server.createContext("/user", exchange -> {
			lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
			exchange.sendResponseHeaders(200, 0);
			exchange.close();
		});

		assertThat(client.testConnection("ghp_test-token")).isTrue();
		assertThat(lastAuthHeader.get()).isEqualTo("Bearer ghp_test-token");
	}

	@Test
	void testConnection_unauthorizedStatus_returnsFalse() throws IOException {
		server.createContext("/user", exchange -> {
			exchange.sendResponseHeaders(401, 0);
			exchange.close();
		});

		assertThat(client.testConnection("bad-token")).isFalse();
	}

	@Test
	void createPullRequest_success_parsesNumberAndUrl() throws IOException {
		server.createContext("/repos/acme/widgets/pulls", exchange -> {
			lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
			lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] body = """
					{"number": 42, "html_url": "https://github.com/acme/widgets/pull/42"}
					""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(201, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});

		GitHubClient.PullRequestResult result = client.createPullRequest("ghp_test-token", "acme", "widgets",
				"agent/task-1-demo", "main", "talos: demo task", "PR body");

		assertThat(result.number()).isEqualTo(42);
		assertThat(result.htmlUrl()).isEqualTo("https://github.com/acme/widgets/pull/42");
		assertThat(lastAuthHeader.get()).isEqualTo("Bearer ghp_test-token");
		assertThat(lastRequestBody.get()).contains("\"head\":\"agent/task-1-demo\"").contains("\"base\":\"main\"");
	}

	@Test
	void createPullRequest_githubRejects_throwsApiExceptionWithoutLeakingToken() throws IOException {
		server.createContext("/repos/acme/widgets/pulls", exchange -> {
			byte[] body = "{\"message\":\"Validation Failed\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(422, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});

		assertThatThrownBy(() -> client.createPullRequest("ghp_test-token", "acme", "widgets", "agent/task-1-demo",
				"main", "talos: demo task", "PR body"))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("422")
				.hasMessageNotContaining("ghp_test-token");
	}
}
