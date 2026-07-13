// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.contracts;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review gap #9: {@code packages/contracts/openapi.yaml} is hand-maintained and drives the
 * generated Angular client, but nothing kept it honest against the running API. This compares
 * path+method *sets* only, not full schemas -- springdoc auto-infers its own schema names/shapes
 * from the Java DTOs, which will never byte-for-byte match the hand-written contract's schemas, so
 * a deep body diff would be pure noise. The actual risk the review named is an endpoint existing
 * in one place and not the other (e.g. the {@code RunSummary.status} enum-vs-string drift and the
 * missing {@code webhooks} client export found live while closing review gap #6), which a
 * path+method set catches.
 */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+",
		"talos.secrets-key=ZGV2LW9ubHktMzItYnl0ZS1wbGFjZWhvbGRlci1rZXk="
})
@AutoConfigureMockMvc
class OpenApiDriftTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Container
	@ServiceConnection
	static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:4.1-management");

	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
	}

	private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void liveApiMatchesTheHandMaintainedContract() throws Exception {
		Set<String> livePaths = liveEndpoints();
		Set<String> contractPaths = contractEndpoints();

		Set<String> missingFromContract = new TreeSet<>(livePaths);
		missingFromContract.removeAll(contractPaths);
		Set<String> missingFromLiveApi = new TreeSet<>(contractPaths);
		missingFromLiveApi.removeAll(livePaths);

		assertThat(missingFromContract)
				.withFailMessage(
						"Endpoints exist in the running API but not in packages/contracts/openapi.yaml: %s",
						missingFromContract)
				.isEmpty();
		assertThat(missingFromLiveApi)
				.withFailMessage(
						"packages/contracts/openapi.yaml documents endpoints the running API doesn't have"
								+ " (renamed/removed?): %s",
						missingFromLiveApi)
				.isEmpty();
	}

	private Set<String> liveEndpoints() throws Exception {
		String token = bearerToken();
		String rawSpec = mockMvc.perform(get("/v3/api-docs").header("Authorization", token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		JsonNode root = new ObjectMapper().readTree(rawSpec);
		Set<String> endpoints = new TreeSet<>();
		for (var pathEntry : root.path("paths").properties()) {
			String path = pathEntry.getKey();
			for (var methodEntry : pathEntry.getValue().properties()) {
				if (HTTP_METHODS.contains(methodEntry.getKey())) {
					endpoints.add(methodEntry.getKey().toUpperCase() + " " + path);
				}
			}
		}
		return endpoints;
	}

	private Set<String> contractEndpoints() throws Exception {
		File contractFile = new File("../../packages/contracts/openapi.yaml");
		JsonNode root = new YAMLMapper().readTree(contractFile);

		Set<String> endpoints = new TreeSet<>();
		for (var pathEntry : root.path("paths").properties()) {
			String rawPath = pathEntry.getKey();
			// Section 10.1: public paths are written relative to the "/api/v1" server entry;
			// internal ones already spell out "/internal/v1" in the path itself (see the
			// servers: block and the /internal/v1/... path keys in openapi.yaml).
			String fullPath = rawPath.startsWith("/internal/v1") ? rawPath : "/api/v1" + rawPath;
			for (var methodEntry : pathEntry.getValue().properties()) {
				if (HTTP_METHODS.contains(methodEntry.getKey())) {
					endpoints.add(methodEntry.getKey().toUpperCase() + " " + fullPath);
				}
			}
		}
		return endpoints;
	}

	private String bearerToken() throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@test.local\",\"password\":\"test-admin-password\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.<String>read(response, "$.token");
	}
}
