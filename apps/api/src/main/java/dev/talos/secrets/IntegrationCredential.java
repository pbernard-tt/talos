// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.secrets;

import dev.talos.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integration_credentials")
public class IntegrationCredential {

	@Id
	private UUID id = UuidV7.generate();

	@Column(name = "integration_id", nullable = false)
	private UUID integrationId;

	@Column(name = "secret_ref", nullable = false)
	private UUID secretRef;

	@Column(name = "auth_mode", nullable = false, length = 30)
	private String authMode;

	@Column(name = "owner_user_id")
	private UUID ownerUserId;

	@Generated(event = EventType.INSERT)
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected IntegrationCredential() {
		// JPA
	}

	public IntegrationCredential(UUID integrationId, UUID secretRef, String authMode, UUID ownerUserId) {
		this.integrationId = integrationId;
		this.secretRef = secretRef;
		this.authMode = authMode;
		this.ownerUserId = ownerUserId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getIntegrationId() {
		return integrationId;
	}

	public UUID getSecretRef() {
		return secretRef;
	}

	public String getAuthMode() {
		return authMode;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
