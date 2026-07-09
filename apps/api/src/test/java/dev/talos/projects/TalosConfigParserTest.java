package dev.talos.projects;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TalosConfigParserTest {

	private final TalosConfigParser parser = new TalosConfigParser();

	// Section 14's example talos.yaml, copied verbatim.
	private static final String VALID_YAML = """
			project:
			  name: example-backend
			  type: spring-boot
			  repo: git@github.com:org/example-backend.git
			  default_branch: main
			stack:
			  language: java
			  runtime: java-21
			  framework: spring-boot
			  package_manager: maven
			commands:
			  install: "./mvnw dependency:resolve"
			  test: "./mvnw test"
			  build: "./mvnw clean package -DskipTests=false"
			  lint: null
			agents:
			  preferred: claude-code
			  allowed: [claude-code, opencode, custom-shell]
			rules:
			  require_approval_for: [production_deploy, database_migration, auth_changes,
			                         security_config, dependency_major_upgrade]
			  forbidden: [commit_secrets, modify_env_files, force_push_main]
			context:
			  docs: [docs/architecture.md, docs/api-guidelines.md]
			  important_paths: [src/main/java, src/main/resources]
			  ignore_paths: [target, node_modules, .env]
			deploy:
			  provider: dokploy
			  app_id: backend-app-id
			  environment: production
			  approval_required: true
			""";

	@Test
	void validConfig_parsesSuccessfully() {
		TalosConfigParser.Result result = parser.parse(VALID_YAML);

		assertThat(result).isInstanceOf(TalosConfigParser.Result.Valid.class);
		var valid = (TalosConfigParser.Result.Valid) result;
		assertThat(valid.parsedJson()).containsKey("project");
	}

	@Test
	void missingRequiredField_isRejectedWithFieldError() {
		String yaml = """
				project:
				  name: example-backend
				  repo: git@github.com:org/example-backend.git
				commands:
				  test: "./mvnw test"
				""";
		// project.type is required and omitted above.

		TalosConfigParser.Result result = parser.parse(yaml);

		assertThat(result).isInstanceOf(TalosConfigParser.Result.Invalid.class);
		var invalid = (TalosConfigParser.Result.Invalid) result;
		assertThat(invalid.fieldErrors()).isNotEmpty();
		assertThat(invalid.fieldErrors().values()).anyMatch(message -> message.toLowerCase().contains("type"));
	}

	@Test
	void unknownAgentKey_isRejectedWithFieldError() {
		String yaml = """
				project:
				  name: example-backend
				  type: spring-boot
				  repo: git@github.com:org/example-backend.git
				commands:
				  test: "./mvnw test"
				agents:
				  preferred: some-made-up-agent
				""";

		TalosConfigParser.Result result = parser.parse(yaml);

		assertThat(result).isInstanceOf(TalosConfigParser.Result.Invalid.class);
		var invalid = (TalosConfigParser.Result.Invalid) result;
		assertThat(invalid.fieldErrors()).containsKey("/agents/preferred");
	}
}
