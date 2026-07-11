package dev.talos.artifacts;

import java.io.IOException;
import java.io.InputStream;

/**
 * Phase 16: run artifacts (transcripts, patches, test reports, generated docs -- Section 4.2)
 * behind an interface so {@link LocalVolumeArtifactStore} (default) and {@link MinioArtifactStore}
 * are swappable, mirroring how {@code DeployProvider} sits behind {@code DeployService}. Only
 * {@code dev.talos.artifacts.ArtifactService} calls this -- talos-api is the only writer, per the
 * same principle as PostgreSQL (Hard constraint 5); worker containers and the Python services never
 * see store credentials.
 */
public interface ArtifactStore {

	/** {@code key} is the logical storage key computed by {@link ArtifactService}, e.g. "{runId}/{kind}/{name}". */
	void write(String key, InputStream content, long contentLength, String contentType) throws IOException;

	InputStream read(String key) throws IOException;

	void delete(String key) throws IOException;
}
