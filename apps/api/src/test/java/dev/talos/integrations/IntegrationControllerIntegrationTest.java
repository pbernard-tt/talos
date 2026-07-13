// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.integrations;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 10.2's Integrations endpoints; GitHubClient is mocked so no live network call is made. */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+",
		"talos.secrets-key=ZGV2LW9ubHktMzItYnl0ZS1wbGFjZWhvbGRlci1rZXk="
})
@AutoConfigureMockMvc
class IntegrationControllerIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

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

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GitHubClient gitHubClient;

	private String bearerToken() throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@test.local\",\"password\":\"test-admin-password\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.<String>read(response, "$.token");
	}

	@Test
	void create_thenList_neverReturnsTheSecret() throws Exception {
		String token = bearerToken();

		String response = mockMvc.perform(post("/api/v1/integrations")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"type":"github","name":"primary-github","secret":"ghp_super-secret-token","authMode":"pat"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("github"))
				.andExpect(jsonPath("$.name").value("primary-github"))
				.andReturn().getResponse().getContentAsString();
		assertNoSecretLeak(response);

		String listResponse = mockMvc.perform(get("/api/v1/integrations").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name=='primary-github')]").exists())
				.andReturn().getResponse().getContentAsString();
		assertNoSecretLeak(listResponse);
	}

	@Test
	void test_delegatesToGitHubClient_andReportsResult() throws Exception {
		String token = bearerToken();
		when(gitHubClient.testConnection("ghp_super-secret-token")).thenReturn(true);

		String response = mockMvc.perform(post("/api/v1/integrations")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"type":"github","name":"tested-github","secret":"ghp_super-secret-token","authMode":"pat"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String integrationId = JsonPath.read(response, "$.id");

		mockMvc.perform(post("/api/v1/integrations/{id}/test", integrationId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true));
	}

	private static void assertNoSecretLeak(String responseBody) {
		org.assertj.core.api.Assertions.assertThat(responseBody).doesNotContain("ghp_super-secret-token");
	}
}
