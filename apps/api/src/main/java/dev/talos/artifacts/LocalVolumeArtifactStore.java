// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.artifacts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Default {@link ArtifactStore}: writes under a single local directory (Section 18's {@code /var/talos/artifacts}). */
public class LocalVolumeArtifactStore implements ArtifactStore {

	private final Path root;

	public LocalVolumeArtifactStore(String rootDir) {
		this.root = Path.of(rootDir);
	}

	@Override
	public void write(String key, InputStream content, long contentLength, String contentType) throws IOException {
		Path target = resolve(key);
		Files.createDirectories(target.getParent());
		Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
	}

	@Override
	public InputStream read(String key) throws IOException {
		return Files.newInputStream(resolve(key));
	}

	@Override
	public void delete(String key) throws IOException {
		Files.deleteIfExists(resolve(key));
	}

	/** Resolves {@code key} under {@link #root}, rejecting anything that would escape it (Section 12.1's spirit -- no path traversal from a caller-supplied key). */
	private Path resolve(String key) {
		Path resolved = root.resolve(key).normalize();
		if (!resolved.startsWith(root)) {
			throw new IllegalArgumentException("artifact key escapes storage root: " + key);
		}
		return resolved;
	}
}
