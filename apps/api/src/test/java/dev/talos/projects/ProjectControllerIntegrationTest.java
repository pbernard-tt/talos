package dev.talos.projects;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
		"talos.jwt-secret=test-only-jwt-signing-secret-not-for-production-use-32bytes+",
		"talos.admin-email=admin@test.local",
		"talos.admin-password=test-admin-password"
})
@AutoConfigureMockMvc
// Phase 15: project CRUD is now MAINTAINER+ (@PreAuthorize on ProjectController).
@WithMockUser(roles = "MAINTAINER")
class ProjectControllerIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Autowired
	private MockMvc mockMvc;

	private static final String VALID_YAML = """
			project:
			  name: example-backend
			  type: spring-boot
			  repo: git@github.com:org/example-backend.git
			commands:
			  test: "./mvnw test"
			""";

	@Test
	void createListGetUpdate_fullCrudRoundTrip() throws Exception {
		String createBody = """
				{"name":"Example Backend","repoUrl":"git@github.com:org/example-backend.git","stackType":"spring-boot"}
				""";

		String createResponse = mockMvc.perform(post("/api/v1/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Example Backend"))
				.andExpect(jsonPath("$.slug").value("example-backend"))
				.andExpect(jsonPath("$.defaultBranch").value("main"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		String id = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

		mockMvc.perform(get("/api/v1/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id=='" + id + "')]").exists())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));

		mockMvc.perform(get("/api/v1/projects/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Example Backend"))
				.andExpect(jsonPath("$.activeConfig").doesNotExist())
				.andExpect(jsonPath("$.recentRuns").isArray())
				.andExpect(jsonPath("$.recentRuns").isEmpty());

		String updateBody = """
				{"name":"Example Backend Renamed","repoUrl":"git@github.com:org/example-backend.git",
				 "defaultBranch":"main","stackType":"spring-boot","status":"ACTIVE"}
				""";
		mockMvc.perform(put("/api/v1/projects/{id}", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Example Backend Renamed"));
	}

	@Test
	void syncConfig_validYaml_versionsAndActivatesCorrectly() throws Exception {
		String createResponse = mockMvc.perform(post("/api/v1/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Sync Project","repoUrl":"git@github.com:org/sync.git","stackType":"spring-boot"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

		mockMvc.perform(post("/api/v1/projects/{id}/sync-config", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"configYaml\":" + toJsonString(VALID_YAML) + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.parsedJson.project.name").value("example-backend"));

		mockMvc.perform(post("/api/v1/projects/{id}/sync-config", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"configYaml\":" + toJsonString(VALID_YAML) + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.active").value(true));

		mockMvc.perform(get("/api/v1/projects/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activeConfig.version").value(2));
	}

	@Test
	void syncConfig_invalidYaml_returns422WithFieldErrors() throws Exception {
		String createResponse = mockMvc.perform(post("/api/v1/projects")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Invalid Config Project","repoUrl":"git@github.com:org/invalid.git","stackType":"spring-boot"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

		String invalidYaml = "project:\n  name: missing-required-fields\n";
		mockMvc.perform(post("/api/v1/projects/{id}/sync-config", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"configYaml\":" + toJsonString(invalidYaml) + "}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.error.code").value("INVALID_CONFIG"))
				.andExpect(jsonPath("$.error.details").isNotEmpty());
	}

	@Test
	void getUnknownProject_returns404() throws Exception {
		mockMvc.perform(get("/api/v1/projects/{id}", "019547c1-0000-7000-8000-000000000000"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
	}

	private static String toJsonString(String raw) {
		return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}
}
