// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.artifacts;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 16: both {@link LocalVolumeArtifactStore} and {@link MinioArtifactStore} must satisfy the
 * same {@link ArtifactStore} contract -- run against both implementations (see
 * {@link LocalVolumeArtifactStoreContractTest} and {@link MinioArtifactStoreContractTest}).
 */
abstract class ArtifactStoreContractTest {

	protected abstract ArtifactStore store();

	@Test
	void writeThenReadReturnsTheSameBytes() throws IOException {
		ArtifactStore store = store();
		byte[] content = "diff --git a/x b/x\n+hello\n".getBytes(StandardCharsets.UTF_8);

		store.write("run1/DIFF_PATCH/diff.patch", new ByteArrayInputStream(content), content.length, "text/x-diff");

		try (InputStream in = store.read("run1/DIFF_PATCH/diff.patch")) {
			assertThat(in.readAllBytes()).isEqualTo(content);
		}
	}

	@Test
	void writeTwiceOverwritesRatherThanAppending() throws IOException {
		ArtifactStore store = store();
		byte[] first = "first".getBytes(StandardCharsets.UTF_8);
		byte[] second = "second-and-longer".getBytes(StandardCharsets.UTF_8);

		store.write("run2/TRANSCRIPT/transcript.jsonl", new ByteArrayInputStream(first), first.length, "application/x-ndjson");
		store.write("run2/TRANSCRIPT/transcript.jsonl", new ByteArrayInputStream(second), second.length, "application/x-ndjson");

		try (InputStream in = store.read("run2/TRANSCRIPT/transcript.jsonl")) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second-and-longer");
		}
	}

	@Test
	void deleteRemovesTheArtifact() throws IOException {
		ArtifactStore store = store();
		byte[] content = "boom\n".getBytes(StandardCharsets.UTF_8);
		store.write("run3/TEST_REPORT/test-report.log", new ByteArrayInputStream(content), content.length, "text/plain");

		store.delete("run3/TEST_REPORT/test-report.log");

		assertThatThrownBy(() -> store.read("run3/TEST_REPORT/test-report.log")).isInstanceOf(IOException.class);
	}

	@Test
	void deleteOfMissingKeyDoesNotThrow() {
		ArtifactStore store = store();
		assertThatCode(() -> store.delete("run4/GENERATED_DOC/does-not-exist.md")).doesNotThrowAnyException();
	}
}
