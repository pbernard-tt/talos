package dev.talos.artifacts;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class MinioArtifactStoreContractTest extends ArtifactStoreContractTest {

	@Container
	static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
			.withEnv("MINIO_ROOT_USER", "test-access-key")
			.withEnv("MINIO_ROOT_PASSWORD", "test-secret-key")
			.withCommand("server", "/data")
			.withExposedPorts(9000)
			.waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

	@Override
	protected ArtifactStore store() {
		String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
		return new MinioArtifactStore(endpoint, "test-access-key", "test-secret-key", "talos-artifacts-test");
	}
}
