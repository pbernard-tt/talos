// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.secrets;

import dev.talos.common.TalosProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * AES-256-GCM secret store (Section 12.2): {@code TALOS_SECRETS_KEY} is a 32-byte base64 key.
 * The only place a {@link SecretValue}'s plaintext is ever materialized -- callers get a
 * {@code secret_ref} UUID back from {@link #encrypt}, never the ciphertext or key.
 */
@Service
public class SecretService {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int GCM_TAG_LENGTH_BITS = 128;
	private static final int NONCE_LENGTH_BYTES = 12;

	private final SecretValueRepository secretValueRepository;
	private final TalosProperties talosProperties;
	private final SecureRandom secureRandom = new SecureRandom();

	public SecretService(SecretValueRepository secretValueRepository, TalosProperties talosProperties) {
		this.secretValueRepository = secretValueRepository;
		this.talosProperties = talosProperties;
	}

	public UUID encrypt(String plaintext) {
		try {
			byte[] nonce = new byte[NONCE_LENGTH_BYTES];
			secureRandom.nextBytes(nonce);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

			SecretValue secretValue = secretValueRepository.save(new SecretValue(ciphertext, nonce));
			return secretValue.getId();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to encrypt secret", e);
		}
	}

	public String decrypt(UUID secretRef) {
		SecretValue secretValue = secretValueRepository.findById(secretRef)
				.orElseThrow(() -> new IllegalStateException("Secret %s not found".formatted(secretRef)));
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key(),
					new GCMParameterSpec(GCM_TAG_LENGTH_BITS, secretValue.getNonce()));
			byte[] plaintext = cipher.doFinal(secretValue.getEncryptedValue());
			return new String(plaintext, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to decrypt secret %s".formatted(secretRef), e);
		}
	}

	/**
	 * Derived lazily, not in the constructor: TALOS_SECRETS_KEY is blank by default in every test
	 * context that doesn't exercise secrets (this @Service is component-scanned everywhere), and
	 * SecretKeySpec rejects an empty key eagerly -- deferring the decode keeps those tests' Spring
	 * contexts loading without each one needing to set this property.
	 */
	private SecretKeySpec key() {
		return new SecretKeySpec(Base64.getDecoder().decode(talosProperties.secretsKey()), "AES");
	}
}
