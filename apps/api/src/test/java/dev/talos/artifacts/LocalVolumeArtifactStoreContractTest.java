package dev.talos.artifacts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LocalVolumeArtifactStoreContractTest extends ArtifactStoreContractTest {

	@TempDir
	Path root;

	@Override
	protected ArtifactStore store() {
		return new LocalVolumeArtifactStore(root.toString());
	}

	@Test
	void rejectsKeysThatWouldEscapeTheStorageRoot() {
		ArtifactStore store = store();
		byte[] content = "x".getBytes();
		assertThatIllegalArgumentException()
				.isThrownBy(() -> store.write("../outside.txt", new ByteArrayInputStream(content), content.length, "text/plain"));
	}
}
