package dev.talos.memory;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class MemorySecretMasker {

	private static final Pattern ASSIGNMENT_SECRET = Pattern.compile(
			"(?i)\\b(api[_-]?key|token|secret|password)\\b\\s*[:=]\\s*([^\\s,;]+)");
	private static final Pattern COMMON_TOKEN = Pattern.compile(
			"\\b(ghp_[A-Za-z0-9_]{16,}|github_pat_[A-Za-z0-9_]{16,}|sk-[A-Za-z0-9_-]{16,})\\b");

	public String mask(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		String masked = ASSIGNMENT_SECRET.matcher(text).replaceAll("$1=***");
		return COMMON_TOKEN.matcher(masked).replaceAll("***");
	}
}
