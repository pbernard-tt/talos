// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.projects;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parses and validates talos.yaml against packages/project-config-schema/talos.schema.json
 * (Section 14). Invalid config is rejected with field-path -> message errors, never partially
 * accepted.
 */
@Component
public class TalosConfigParser {

	private final YAMLMapper yamlMapper = new YAMLMapper();
	private final ObjectMapper jsonMapper = new ObjectMapper();
	private final Schema schema;

	public TalosConfigParser() {
		try (InputStream in = new ClassPathResource("talos.schema.json").getInputStream()) {
			String schemaJson = new String(in.readAllBytes());
			SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
			this.schema = registry.getSchema(schemaJson);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load talos.schema.json from the classpath", e);
		}
	}

	public sealed interface Result permits Result.Valid, Result.Invalid {
		record Valid(Map<String, Object> parsedJson) implements Result {
		}

		record Invalid(Map<String, String> fieldErrors) implements Result {
		}
	}

	public Result parse(String configYaml) {
		JsonNode node;
		try {
			node = yamlMapper.readTree(configYaml);
		} catch (RuntimeException e) {
			return new Result.Invalid(Map.of("yaml", "Could not parse YAML: " + e.getMessage()));
		}

		List<Error> errors = schema.validate(node);
		if (!errors.isEmpty()) {
			Map<String, String> fieldErrors = new TreeMap<>();
			for (Error error : errors) {
				fieldErrors.put(error.getInstanceLocation().toString(), error.getMessage());
			}
			return new Result.Invalid(fieldErrors);
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> parsedJson = jsonMapper.convertValue(node, LinkedHashMap.class);
		return new Result.Valid(parsedJson);
	}
}
