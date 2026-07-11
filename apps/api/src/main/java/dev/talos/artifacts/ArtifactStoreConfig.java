package dev.talos.artifacts;

import dev.talos.common.TalosProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects the {@link ArtifactStore} bean from {@code talos.artifact-store-type} (default "local"
 * -- Phase 16's acceptance criterion that unconfigured behavior is exactly pre-Phase-16). */
@Configuration
public class ArtifactStoreConfig {

	@Bean
	@ConditionalOnProperty(prefix = "talos", name = "artifact-store-type", havingValue = "local", matchIfMissing = true)
	public ArtifactStore localVolumeArtifactStore(TalosProperties properties) {
		return new LocalVolumeArtifactStore(properties.artifactLocalDir());
	}

	@Bean
	@ConditionalOnProperty(prefix = "talos", name = "artifact-store-type", havingValue = "minio")
	public ArtifactStore minioArtifactStore(TalosProperties properties) {
		return new MinioArtifactStore(properties.minioEndpoint(), properties.minioAccessKey(),
				properties.minioSecretKey(), properties.minioBucket());
	}
}
