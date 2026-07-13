// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Section 16 Phase 15: user management (create, list, role assignment, deactivation) -- OWNER only. */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password"
})
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

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

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String ownerToken() throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@test.local\",\"password\":\"test-admin-password\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.<String>read(response, "$.token");
	}

	private String loginAs(Role role) throws Exception {
		String email = "users-" + UUID.randomUUID() + "@test.local";
		userRepository.save(new User(email, "Test " + role, passwordEncoder.encode("test-password-12345"), role));
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"test-password-12345\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.<String>read(response, "$.token");
	}

	@Test
	void owner_createsListsAndUpdatesAUser() throws Exception {
		String ownerToken = ownerToken();
		String email = "new-" + UUID.randomUUID() + "@test.local";

		String createResponse = mockMvc.perform(post("/api/v1/users")
						.header("Authorization", ownerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","name":"New User","password":"a-strong-password","role":"VIEWER"}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.role").value("VIEWER"))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn().getResponse().getContentAsString();
		String userId = JsonPath.read(createResponse, "$.id");

		mockMvc.perform(get("/api/v1/users").header("Authorization", ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.email=='" + email + "')]").exists());

		mockMvc.perform(patch("/api/v1/users/{id}", userId)
						.header("Authorization", ownerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"MAINTAINER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("MAINTAINER"));

		mockMvc.perform(patch("/api/v1/users/{id}", userId)
						.header("Authorization", ownerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		assertThat(userRepository.findById(UUID.fromString(userId)).orElseThrow().isActive()).isFalse();
	}

	@Test
	void createUser_duplicateEmail_returns409() throws Exception {
		String ownerToken = ownerToken();
		mockMvc.perform(post("/api/v1/users")
						.header("Authorization", ownerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@test.local\",\"name\":\"Dup\",\"password\":\"a-strong-password\",\"role\":\"VIEWER\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
	}

	@Test
	void ownerCannotChangeTheirOwnRoleOrDeactivateThemselves() throws Exception {
		String ownerToken = ownerToken();
		User admin = userRepository.findByEmail("admin@test.local").orElseThrow();

		mockMvc.perform(patch("/api/v1/users/{id}", admin.getId())
						.header("Authorization", ownerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"role\":\"VIEWER\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("CANNOT_MODIFY_OWN_ACCOUNT"));

		mockMvc.perform(patch("/api/v1/users/{id}", admin.getId())
						.header("Authorization", ownerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("CANNOT_MODIFY_OWN_ACCOUNT"));

		assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.OWNER);
	}

	@Test
	void nonOwnerRolesCannotAccessUserManagement() throws Exception {
		String maintainerToken = loginAs(Role.MAINTAINER);

		mockMvc.perform(get("/api/v1/users").header("Authorization", maintainerToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		mockMvc.perform(post("/api/v1/users")
						.header("Authorization", maintainerToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"blocked@test.local\",\"name\":\"Blocked\",\"password\":\"a-strong-password\",\"role\":\"VIEWER\"}"))
				.andExpect(status().isForbidden());
	}
}
