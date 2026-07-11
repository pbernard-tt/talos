package dev.talos.artifacts;

import dev.talos.common.TalosProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Phase 16's one-shot migration: copies every already-recorded artifact from the local volume into
 * the currently-configured {@code ArtifactStore} (expected to be MinIO), for an install switching
 * from the local-volume default. Runs once at boot when {@code talos.migrate-artifacts-on-boot} is
 * true, then does nothing on subsequent boots unless the operator sets it again -- deliberately a
 * boot-time flag rather than a separate CLI entrypoint, mirroring {@link
 * dev.talos.auth.IntegrationServiceAccountSeeder}'s existing idempotent-seeder shape rather than
 * inventing a new tooling surface for a copy that only ever needs to run once per install.
 */
@Component
@ConditionalOnProperty(prefix = "talos", name = "migrate-artifacts-on-boot", havingValue = "true")
public class ArtifactMigrationRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ArtifactMigrationRunner.class);

	private final TalosProperties properties;
	private final RunArtifactRepository runArtifactRepository;
	private final ArtifactStore destinationStore;

	public ArtifactMigrationRunner(TalosProperties properties, RunArtifactRepository runArtifactRepository,
			ArtifactStore destinationStore) {
		this.properties = properties;
		this.runArtifactRepository = runArtifactRepository;
		this.destinationStore = destinationStore;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!"minio".equals(properties.artifactStoreType())) {
			log.warn("talos.migrate-artifacts-on-boot=true but talos.artifact-store-type is not \"minio\" -- "
					+ "nothing to migrate to, skipping");
			return;
		}

		ArtifactStore source = new LocalVolumeArtifactStore(properties.artifactLocalDir());
		List<RunArtifact> artifacts = runArtifactRepository.findAll();
		int migrated = 0;
		for (RunArtifact artifact : artifacts) {
			try (InputStream in = source.read(artifact.getStorageKey())) {
				destinationStore.write(artifact.getStorageKey(), in, artifact.getSizeBytes(), artifact.getContentType());
				migrated++;
			} catch (IOException e) {
				log.warn("failed to migrate artifact {} ({}): {}", artifact.getId(), artifact.getStorageKey(),
						e.getMessage());
			}
		}
		log.info("artifact migration: copied {}/{} local artifact(s) into the configured MinIO store", migrated,
				artifacts.size());
	}
}
