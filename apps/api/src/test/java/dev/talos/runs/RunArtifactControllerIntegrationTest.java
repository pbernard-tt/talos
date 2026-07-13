// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.runs;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 16: POST/GET/DELETE /internal/v1/runs/{id}/artifacts and GET /api/v1/runs/{id}/artifacts(/{artifactId}/download). */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+",
		"talos.secrets-key=ZGV2LW9ubHktMzItYnl0ZS1wbGFjZWhvbGRlci1rZXk="
})
@AutoConfigureMockMvc
class RunArtifactControllerIntegrationTest {

	private static final String INTERNAL_TOKEN = "test-internal-token-not-for-production-use-32bytes+";

	@MockitoBean
	private dev.talos.integrations.GitHubClient gitHubClient;

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4.1-management");

	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws java.io.IOException {
		registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
		// LocalVolumeArtifactStore's default (talos.artifact-local-dir=/var/talos/artifacts) isn't
		// writable outside a real deployment -- point it at a throwaway directory for this test class.
		java.nio.file.Path artifactDir = java.nio.file.Files.createTempDirectory("talos-test-artifacts");
		registry.add("talos.artifact-local-dir", artifactDir::toString);
	}

	@Autowired
	private MockMvc mockMvc;

	private static String cachedBearerToken;

	private String bearerToken() throws Exception {
		if (cachedBearerToken == null) {
			String response = mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"email\":\"admin@test.local\",\"password\":\"test-admin-password\"}"))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			cachedBearerToken = "Bearer " + JsonPath.<String>read(response, "$.token");
		}
		return cachedBearerToken;
	}

	private String createRun() throws Exception {
		String token = bearerToken();
		String projectResponse = mockMvc.perform(post("/api/v1/projects")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Artifact Project %s","repoUrl":"git@github.com:org/artifacts.git","stackType":"spring-boot"}
								""".formatted(java.util.UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String projectId = JsonPath.read(projectResponse, "$.id");

		String taskResponse = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Artifact task\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String taskId = JsonPath.read(taskResponse, "$.id");

		String runResponse = mockMvc.perform(post("/api/v1/tasks/{id}/start-run", taskId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentKey\":\"custom-shell\",\"authMode\":\"api_key\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(runResponse, "$.id");
	}

	private String uploadArtifact(String runId, String kind, String name, byte[] content) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", name, "text/plain", content);
		String response = mockMvc.perform(multipart("/internal/v1/runs/{id}/artifacts", runId)
						.file(file)
						.param("kind", kind)
						.param("name", name)
						.header("X-Talos-Internal-Token", INTERNAL_TOKEN))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	@Test
	void uploadArtifact_thenListsAndDownloadsByteIdentical() throws Exception {
		String runId = createRun();
		byte[] content = "diff --git a/x b/x\n+hello\n".getBytes(StandardCharsets.UTF_8);

		String artifactId = uploadArtifact(runId, "DIFF_PATCH", "diff.patch", content);

		String token = bearerToken();
		mockMvc.perform(get("/api/v1/runs/{id}/artifacts", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(artifactId))
				.andExpect(jsonPath("$[0].kind").value("DIFF_PATCH"))
				.andExpect(jsonPath("$[0].name").value("diff.patch"))
				.andExpect(jsonPath("$[0].sizeBytes").value(content.length));

		byte[] downloaded = mockMvc.perform(get("/api/v1/runs/{id}/artifacts/{artifactId}/download", runId, artifactId)
						.header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getHeader("Content-Disposition"))
						.contains("diff.patch"))
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(downloaded).isEqualTo(content);
	}

	@Test
	void uploadArtifact_withoutServiceToken_returns401() throws Exception {
		String runId = createRun();
		MockMultipartFile file = new MockMultipartFile("file", "diff.patch", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(multipart("/internal/v1/runs/{id}/artifacts", runId)
						.file(file)
						.param("kind", "DIFF_PATCH")
						.param("name", "diff.patch"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void uploadArtifact_withUnsafeName_returns422() throws Exception {
		String runId = createRun();
		MockMultipartFile file = new MockMultipartFile("file", "x", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(multipart("/internal/v1/runs/{id}/artifacts", runId)
						.file(file)
						.param("kind", "DIFF_PATCH")
						.param("name", "../escape.patch")
						.header("X-Talos-Internal-Token", INTERNAL_TOKEN))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("INVALID_ARTIFACT_NAME"));
	}

	@Test
	void downloadArtifact_unknownArtifactId_returns404() throws Exception {
		String runId = createRun();
		String token = bearerToken();
		mockMvc.perform(get("/api/v1/runs/{id}/artifacts/{artifactId}/download", runId, java.util.UUID.randomUUID())
						.header("Authorization", token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("ARTIFACT_NOT_FOUND"));
	}

	@Test
	void downloadArtifact_belongingToAnotherRun_returns404() throws Exception {
		String runId1 = createRun();
		String runId2 = createRun();
		String artifactId = uploadArtifact(runId1, "TRANSCRIPT", "transcript.jsonl", "{}".getBytes(StandardCharsets.UTF_8));

		String token = bearerToken();
		mockMvc.perform(get("/api/v1/runs/{id}/artifacts/{artifactId}/download", runId2, artifactId)
						.header("Authorization", token))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteArtifacts_removesThemFromListAndDownload() throws Exception {
		String runId = createRun();
		String artifactId = uploadArtifact(runId, "TEST_REPORT", "test-report.log", "boom\n".getBytes(StandardCharsets.UTF_8));
		String token = bearerToken();

		mockMvc.perform(delete("/internal/v1/runs/{id}/artifacts", runId).header("X-Talos-Internal-Token", INTERNAL_TOKEN))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/runs/{id}/artifacts", runId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());

		mockMvc.perform(get("/api/v1/runs/{id}/artifacts/{artifactId}/download", runId, artifactId)
						.header("Authorization", token))
				.andExpect(status().isNotFound());
	}
}
