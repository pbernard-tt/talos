package dev.talos.secrets;

import dev.talos.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * AES-256-GCM encrypted secret storage (Section 12.2). Read/written ONLY by dev.talos.secrets;
 * never exposed via REST — no controller or DTO may reference this entity or its fields.
 */
@Entity
@Table(name = "secret_values")
public class SecretValue {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "encrypted_value", nullable = false)
	private byte[] encryptedValue;

	@Column(nullable = false)
	private byte[] nonce;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected SecretValue() {
		// JPA
	}

	public SecretValue(byte[] encryptedValue, byte[] nonce) {
		this.encryptedValue = encryptedValue;
		this.nonce = nonce;
	}

	public UUID getId() {
		return id;
	}

	public byte[] getEncryptedValue() {
		return encryptedValue;
	}

	public byte[] getNonce() {
		return nonce;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
