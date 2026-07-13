// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.tasks;

import dev.talos.audit.AuditEvent;
import dev.talos.audit.AuditEventRepository;
import dev.talos.auth.User;
import dev.talos.auth.UserRepository;
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
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses a real login (not @WithMockUser) so the request carries a JWT that resolves to a real
 * AuthenticatedUser principal — task mutations need the actor's user id for audit rows and
 * requestedBy, which a @WithMockUser UserDetails principal can't supply.
 */
@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password"
})
@AutoConfigureMockMvc
class TaskControllerIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void disableUnusedIntegrations(DynamicPropertyRegistry registry) {
		registry.add("spring.rabbitmq.addresses", () -> "amqp://guest:guest@localhost:5672");
		registry.add("spring.data.redis.url",
				() -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuditEventRepository auditEventRepository;

	private String bearerToken() throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"admin@test.local\",\"password\":\"test-admin-password\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + com.jayway.jsonpath.JsonPath.<String>read(response, "$.token");
	}

	private String createProject(String token) throws Exception {
		String response = mockMvc.perform(post("/api/v1/projects")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Board Project","repoUrl":"git@github.com:org/board.git","stackType":"spring-boot"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(response, "$.id");
	}

	@Test
	void createListGetPatch_fullCrudRoundTrip() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);

		String createBody = "{\"projectId\":\"" + projectId + "\",\"title\":\"Add /hello endpoint\"}";
		String createResponse = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Add /hello endpoint"))
				.andExpect(jsonPath("$.status").value("BACKLOG"))
				.andExpect(jsonPath("$.priority").value("MEDIUM"))
				.andExpect(jsonPath("$.boardPosition").value(0))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		String taskId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

		mockMvc.perform(get("/api/v1/tasks").header("Authorization", token)
						.param("projectId", projectId)
						.param("status", "BACKLOG"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id=='" + taskId + "')]").exists());

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Add /hello endpoint"))
				.andExpect(jsonPath("$.runs").isArray())
				.andExpect(jsonPath("$.runs").isEmpty());

		mockMvc.perform(patch("/api/v1/tasks/{id}", taskId).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Add /hello endpoint (renamed)\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Add /hello endpoint (renamed)"))
				.andExpect(jsonPath("$.status").value("BACKLOG"));

		User admin = userRepository.findByEmail("admin@test.local").orElseThrow();
		List<AuditEvent> createdEvents = auditEventRepository.findAll().stream()
				.filter(e -> "task.created".equals(e.getEventType()) && admin.getId().equals(e.getActorUserId()))
				.toList();
		assertThat(createdEvents).isNotEmpty();
	}

	@Test
	void move_legalTransition_persistsAndAudits() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String createResponse = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Move me\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String taskId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

		// boardPosition is requested as 1 but the READY column is empty (only one task, being
		// moved into it) -- move() clamps to the column's actual size, so this lands at 0, not 1.
		mockMvc.perform(post("/api/v1/tasks/{id}/move", taskId).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"READY\",\"boardPosition\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READY"))
				.andExpect(jsonPath("$.boardPosition").value(0));

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READY"));

		List<AuditEvent> movedEvents = auditEventRepository.findAll().stream()
				.filter(e -> "task.moved".equals(e.getEventType()) && taskId.equals(String.valueOf(e.getEntityId())))
				.toList();
		assertThat(movedEvents).isNotEmpty();
	}

	@Test
	void move_illegalTransition_returns422AndDoesNotPersist() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String createResponse = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"Stuck in backlog\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String taskId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

		mockMvc.perform(post("/api/v1/tasks/{id}/move", taskId).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"DONE\",\"boardPosition\":0}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("ILLEGAL_TRANSITION"));

		mockMvc.perform(get("/api/v1/tasks/{id}", taskId).header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("BACKLOG"));
	}

	@Test
	void move_intoAColumnWithExistingTasks_insertsAtRequestedIndexWithoutDuplicatePositions() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String firstId = createTask(token, projectId, "First");
		String secondId = createTask(token, projectId, "Second");
		String thirdId = createTask(token, projectId, "Third");
		move(token, firstId, "READY", 0);
		move(token, secondId, "READY", 1);

		// Insert "Third" at index 0 -- "First"/"Second" must shift to 1/2, not collide with it.
		move(token, thirdId, "READY", 0);

		String listResponse = mockMvc.perform(get("/api/v1/tasks")
						.header("Authorization", token)
						.param("projectId", projectId)
						.param("status", "READY")
						.param("size", "10"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<Integer> positions = com.jayway.jsonpath.JsonPath.read(listResponse, "$.content[*].boardPosition");
		assertThat(positions).containsExactlyInAnyOrder(0, 1, 2);
		assertThat(new java.util.HashSet<>(positions)).hasSize(3);
	}

	@Test
	void move_outOfAColumn_renumbersRemainingSiblingsContiguously() throws Exception {
		String token = bearerToken();
		String projectId = createProject(token);
		String firstId = createTask(token, projectId, "First");
		String secondId = createTask(token, projectId, "Second");
		String thirdId = createTask(token, projectId, "Third");
		move(token, firstId, "READY", 0);
		move(token, secondId, "READY", 1);
		move(token, thirdId, "READY", 2);

		// Pull the middle task out of READY -- "First"/"Third" must close the gap to 0/1, not
		// leave a hole at position 1.
		move(token, secondId, "BACKLOG", 0);

		String listResponse = mockMvc.perform(get("/api/v1/tasks")
						.header("Authorization", token)
						.param("projectId", projectId)
						.param("status", "READY")
						.param("size", "10"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<Integer> positions = com.jayway.jsonpath.JsonPath.read(listResponse, "$.content[*].boardPosition");
		assertThat(positions).containsExactlyInAnyOrder(0, 1);
	}

	private String createTask(String token, String projectId, String title) throws Exception {
		String createResponse = mockMvc.perform(post("/api/v1/tasks")
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"projectId\":\"" + projectId + "\",\"title\":\"" + title + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");
	}

	private void move(String token, String taskId, String status, int boardPosition) throws Exception {
		mockMvc.perform(post("/api/v1/tasks/{id}/move", taskId).header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"" + status + "\",\"boardPosition\":" + boardPosition + "}"))
				.andExpect(status().isOk());
	}

	@Test
	void getUnknownTask_returns404() throws Exception {
		String token = bearerToken();
		mockMvc.perform(get("/api/v1/tasks/{id}", "019547c1-0000-7000-8000-000000000000")
						.header("Authorization", token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));
	}
}
