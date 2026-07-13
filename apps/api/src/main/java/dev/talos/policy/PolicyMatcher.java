// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.policy;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

/**
 * Section 12.2: file patterns are gitignore-style globs matched against changed paths, command
 * patterns are matched as substrings of captured command lines.
 */
public final class PolicyMatcher {

	private PolicyMatcher() {
	}

	/**
	 * A pattern containing "/" matches the full relative path; a bare pattern (e.g. ".env*", no
	 * "/") matches the file's basename at any depth, mirroring gitignore semantics -- so ".env*"
	 * flags "backend/.env.local", not just a top-level ".env".
	 */
	public static boolean matchesFile(String pattern, String filePath) {
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
		Path path = Path.of(filePath);
		if (pattern.indexOf('/') >= 0) {
			return matcher.matches(path);
		}
		Path fileName = path.getFileName();
		return fileName != null && matcher.matches(fileName);
	}

	public static boolean matchesCommand(String pattern, String line) {
		return line != null && line.contains(pattern);
	}

	/** Returns the first pattern in the list that matches, or null. */
	public static String firstFileMatch(List<String> patterns, String filePath) {
		for (String pattern : patterns) {
			if (matchesFile(pattern, filePath)) {
				return pattern;
			}
		}
		return null;
	}

	public static String firstCommandMatch(List<String> patterns, String line) {
		for (String pattern : patterns) {
			if (matchesCommand(pattern, line)) {
				return pattern;
			}
		}
		return null;
	}
}
