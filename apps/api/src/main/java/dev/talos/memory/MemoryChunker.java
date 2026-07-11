package dev.talos.memory;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MemoryChunker {

	private static final int DEFAULT_MAX_CHARS = 1200;

	public List<String> chunk(String content) {
		return chunk(content, DEFAULT_MAX_CHARS);
	}

	List<String> chunk(String content, int maxChars) {
		String normalized = (content == null ? "" : content).trim();
		if (normalized.isEmpty()) {
			return List.of();
		}
		List<String> chunks = new ArrayList<>();
		int start = 0;
		while (start < normalized.length()) {
			int end = Math.min(start + maxChars, normalized.length());
			if (end < normalized.length()) {
				int whitespace = normalized.lastIndexOf(' ', end);
				if (whitespace > start + maxChars / 2) {
					end = whitespace;
				}
			}
			String chunk = normalized.substring(start, end).trim();
			if (!chunk.isEmpty()) {
				chunks.add(chunk);
			}
			start = end;
			while (start < normalized.length() && Character.isWhitespace(normalized.charAt(start))) {
				start++;
			}
		}
		return chunks;
	}
}
