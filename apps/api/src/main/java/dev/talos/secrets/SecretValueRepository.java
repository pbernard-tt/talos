// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.secrets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Package-private-by-convention: only dev.talos.secrets components should inject this repository. */
public interface SecretValueRepository extends JpaRepository<SecretValue, UUID> {
}
