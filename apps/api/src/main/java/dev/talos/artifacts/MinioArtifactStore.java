// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.artifacts;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;

import java.io.IOException;
import java.io.InputStream;

/** S3-compatible {@link ArtifactStore} (Phase 16). Credentials never leave this class -- no log
 * statement or exception message here includes {@link #accessKey}/{@link #secretKey}. */
public class MinioArtifactStore implements ArtifactStore {

	private final MinioClient client;
	private final String bucket;

	public MinioArtifactStore(String endpoint, String accessKey, String secretKey, String bucket) {
		this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
		this.bucket = bucket;
		ensureBucket();
	}

	private void ensureBucket() {
		try {
			boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
			if (!exists) {
				client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
			}
		} catch (MinioException e) {
			throw new IllegalStateException("failed to ensure MinIO bucket " + bucket + " exists", e);
		}
	}

	@Override
	public void write(String key, InputStream content, long contentLength, String contentType) throws IOException {
		try {
			client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
					.stream(content, contentLength, -1L)
					.contentType(contentType)
					.build());
		} catch (MinioException e) {
			throw new IOException("failed to write artifact " + key + " to MinIO", e);
		}
	}

	@Override
	public InputStream read(String key) throws IOException {
		try {
			return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
		} catch (MinioException e) {
			throw new IOException("failed to read artifact " + key + " from MinIO", e);
		}
	}

	@Override
	public void delete(String key) throws IOException {
		try {
			client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
		} catch (MinioException e) {
			throw new IOException("failed to delete artifact " + key + " from MinIO", e);
		}
	}
}
