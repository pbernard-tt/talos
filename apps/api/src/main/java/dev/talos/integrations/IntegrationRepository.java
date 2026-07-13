// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IntegrationRepository extends JpaRepository<Integration, UUID> {
}
