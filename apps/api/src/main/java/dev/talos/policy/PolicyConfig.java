package dev.talos.policy;

import dev.talos.common.TalosProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the global policy.yaml (Section 12.2/12.3): the bundled classpath default, or
 * TALOS_POLICY_FILE if set. Loaded once at startup -- policy changes require a restart, matching
 * every other TalosProperties-driven config in this service.
 */
@Component
public class PolicyConfig {

	private final YAMLMapper yamlMapper = new YAMLMapper();
	private final PolicyRules rules;

	public PolicyConfig(TalosProperties properties) {
		this.rules = load(properties.policyFile());
	}

	public PolicyRules rules() {
		return rules;
	}

	private PolicyRules load(String overridePath) {
		Resource resource = (overridePath != null && !overridePath.isBlank())
				? new FileSystemResource(overridePath)
				: new ClassPathResource("policy.yaml");
		try (InputStream in = resource.getInputStream()) {
			JsonNode root = yamlMapper.readTree(in);
			return new PolicyRules(
					stringList(root.at("/commands/blocked")),
					stringList(root.at("/commands/approval_required")),
					stringList(root.at("/files/blocked_patterns")),
					stringList(root.at("/files/approval_required_patterns")));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load policy.yaml from " + resource, e);
		}
	}

	private static List<String> stringList(JsonNode node) {
		if (node == null || node.isMissingNode() || !node.isArray()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		node.forEach(n -> out.add(n.asString()));
		return out;
	}
}
