package dev.talos.memory;

import com.jayway.jsonpath.JsonPath;
import dev.talos.projects.Project;
import dev.talos.projects.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password",
		"talos.internal-api-token=test-internal-token-not-for-production-use-32bytes+"
})
@AutoConfigureMockMvc
class MemoryControllerIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(org.testcontainers.utility.DockerImageName
			.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ProjectRepository projectRepository;

	@Test
	void publicAndInternalIngestionFeedInternalProjectScopedSearch() throws Exception {
		Project project = projectRepository.save(new Project("Memory API", "memory-api", "git@example:memory.git", "main", "python"));

		mockMvc.perform(post("/api/v1/projects/{id}/memory/documents", project.getId())
						.header("Authorization", bearerToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "sourceType": "OPERATOR_NOTE",
								  "sourceRef": "note-1",
								  "title": "Operator note",
								  "content": "Use AccountService for invoice reconciliation."
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sourceType").value("OPERATOR_NOTE"))
				.andExpect(jsonPath("$.sourceRef").value("note-1"));

		mockMvc.perform(post("/internal/v1/projects/{id}/memory/documents", project.getId())
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "sourceType": "CONTEXT_DOC",
								  "sourceRef": "docs/architecture.md",
								  "title": "Architecture",
								  "content": "Route payment reconciliation through PaymentsGateway."
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sourceType").value("CONTEXT_DOC"));

		String searchResponse = mockMvc.perform(get("/internal/v1/projects/{id}/memory/search", project.getId())
						.header("X-Talos-Internal-Token", "test-internal-token-not-for-production-use-32bytes+")
						.param("query", "invoice AccountService")
						.param("budgetChars", "2000"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(JsonPath.<String>read(searchResponse, "$.results[0].content")).contains("AccountService");
	}

	private String bearerToken() throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "admin@test.local",
								  "password": "test-admin-password"
								}
								"""))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return "Bearer " + JsonPath.<String>read(response, "$.token");
	}
}
