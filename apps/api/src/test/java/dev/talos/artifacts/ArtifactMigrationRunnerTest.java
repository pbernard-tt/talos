package dev.talos.artifacts;

import dev.talos.common.TalosProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Phase 16's one-shot local -> MinIO migration (round-trip: bytes written locally come back out
 * of the destination store byte-identical, keyed the same way ArtifactService already keys them). */
@ExtendWith(MockitoExtension.class)
class ArtifactMigrationRunnerTest {

	@Mock
	private RunArtifactRepository runArtifactRepository;

	@TempDir
	Path localDir;

	@TempDir
	Path destinationDir;

	private TalosProperties properties(String artifactStoreType) {
		return new TalosProperties(null, null, null, null, null, null, null, null, null, null, null, null, null,
				artifactStoreType, localDir.toString(), null, null, null, null, null, null);
	}

	@Test
	void copiesEveryRecordedArtifactFromLocalIntoTheDestinationStore() throws IOException {
		UUID runId = UUID.randomUUID();
		String key = runId + "/DIFF_PATCH/diff.patch";
		byte[] content = "diff --git a b\n".getBytes(StandardCharsets.UTF_8);
		Path sourceFile = localDir.resolve(key);
		Files.createDirectories(sourceFile.getParent());
		Files.write(sourceFile, content);

		RunArtifact artifact = new RunArtifact(runId, ArtifactKind.DIFF_PATCH, "diff.patch", key, "text/x-diff", content.length);
		when(runArtifactRepository.findAll()).thenReturn(List.of(artifact));

		ArtifactStore destination = new LocalVolumeArtifactStore(destinationDir.toString());
		ArtifactMigrationRunner runner = new ArtifactMigrationRunner(properties("minio"), runArtifactRepository, destination);

		runner.run(null);

		try (InputStream in = destination.read(key)) {
			assertThat(in.readAllBytes()).isEqualTo(content);
		}
	}

	@Test
	void skipsMigrationWhenArtifactStoreTypeIsNotMinio() {
		ArtifactStore destination = new LocalVolumeArtifactStore(destinationDir.toString());
		ArtifactMigrationRunner runner = new ArtifactMigrationRunner(properties("local"), runArtifactRepository, destination);

		runner.run(null);

		verifyNoInteractions(runArtifactRepository);
	}

	@Test
	void aFailedIndividualCopyDoesNotAbortTheRestOfTheMigration() throws IOException {
		UUID missingRunId = UUID.randomUUID();
		RunArtifact missing = new RunArtifact(missingRunId, ArtifactKind.TRANSCRIPT, "transcript.jsonl",
				missingRunId + "/TRANSCRIPT/transcript.jsonl", "application/x-ndjson", 10);

		UUID okRunId = UUID.randomUUID();
		String okKey = okRunId + "/TEST_REPORT/test-report.log";
		byte[] okContent = "ok\n".getBytes(StandardCharsets.UTF_8);
		Path okFile = localDir.resolve(okKey);
		Files.createDirectories(okFile.getParent());
		Files.write(okFile, okContent);
		RunArtifact ok = new RunArtifact(okRunId, ArtifactKind.TEST_REPORT, "test-report.log", okKey, "text/plain", okContent.length);

		when(runArtifactRepository.findAll()).thenReturn(List.of(missing, ok));

		ArtifactStore destination = new LocalVolumeArtifactStore(destinationDir.toString());
		ArtifactMigrationRunner runner = new ArtifactMigrationRunner(properties("minio"), runArtifactRepository, destination);

		runner.run(null);

		try (InputStream in = destination.read(okKey)) {
			assertThat(in.readAllBytes()).isEqualTo(okContent);
		}
	}
}
