// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.artifacts;

import dev.talos.audit.AuditService;
import dev.talos.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Phase 16: the only caller of {@link ArtifactStore} -- talos-api is the sole writer to it, same
 * principle as PostgreSQL (Hard constraint 5). Owns the {@code run_artifacts} metadata table and
 * the storage-key layout ("{runId}/{kind}/{name}", one subtree per artifact class per run). */
@Service
public class ArtifactService {

	// Runner-supervisor-supplied artifact names must not be used as-is in a storage key --
	// disallow path separators/".." so a crafted name can't escape LocalVolumeArtifactStore's root
	// (belt-and-suspenders alongside LocalVolumeArtifactStore's own resolve() check).
	private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

	private final RunArtifactRepository runArtifactRepository;
	private final ArtifactStore artifactStore;
	private final AuditService auditService;

	public ArtifactService(RunArtifactRepository runArtifactRepository, ArtifactStore artifactStore,
			AuditService auditService) {
		this.runArtifactRepository = runArtifactRepository;
		this.artifactStore = artifactStore;
		this.auditService = auditService;
	}

	public RunArtifact store(UUID runId, ArtifactKind kind, String name, InputStream content, long contentLength,
			String contentType) {
		if (!SAFE_NAME.matcher(name).matches()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ARTIFACT_NAME",
					"Artifact name must match [A-Za-z0-9._-]+");
		}
		String key = runId + "/" + kind.name() + "/" + name;
		try {
			artifactStore.write(key, content, contentLength, contentType);
		} catch (IOException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "ARTIFACT_STORE_WRITE_FAILED",
					"Failed to write artifact to the configured store");
		}
		RunArtifact artifact = runArtifactRepository
				.save(new RunArtifact(runId, kind, name, key, contentType, contentLength));
		auditService.record(null, "run.artifact.recorded", "run", runId,
				Map.of("artifactId", artifact.getId().toString(), "kind", kind.name(), "name", name));
		return artifact;
	}

	public List<RunArtifact> list(UUID runId) {
		return runArtifactRepository.findByRunId(runId);
	}

	public RunArtifact getOrThrow(UUID runId, UUID artifactId) {
		RunArtifact artifact = runArtifactRepository.findById(artifactId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ARTIFACT_NOT_FOUND", "Artifact not found"));
		if (!artifact.getRunId().equals(runId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "ARTIFACT_NOT_FOUND", "Artifact not found");
		}
		return artifact;
	}

	public InputStream download(RunArtifact artifact) {
		try {
			return artifactStore.read(artifact.getStorageKey());
		} catch (IOException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "ARTIFACT_STORE_READ_FAILED",
					"Failed to read artifact from the configured store");
		}
	}

	/** Phase 11's retention sweep, extended: deletes every stored artifact for a run plus its metadata rows. */
	public void deleteForRun(UUID runId) {
		List<RunArtifact> artifacts = runArtifactRepository.findByRunId(runId);
		for (RunArtifact artifact : artifacts) {
			try {
				artifactStore.delete(artifact.getStorageKey());
			} catch (IOException e) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "ARTIFACT_STORE_DELETE_FAILED",
						"Failed to delete artifact from the configured store");
			}
		}
		runArtifactRepository.deleteAll(artifacts);
	}
}
