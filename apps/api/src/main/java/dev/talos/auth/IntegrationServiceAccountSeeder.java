// SPDX-FileCopyrightText: 2026 Vulkan Technologies
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.talos.auth;

import dev.talos.common.TalosProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the Telegram/WhatsApp chat trigger service accounts (Section 16 Phase 12 Track B) from
 * TALOS_{TELEGRAM,WHATSAPP}_SERVICE_EMAIL/PASSWORD, mirroring {@link AdminSeeder}. Idempotent.
 * Role is MAINTAINER (Phase 15, revised from the Phase 12 placeholder VIEWER): these accounts must
 * pass the {@code @PreAuthorize("hasRole('MAINTAINER')")} gate on {@code POST /api/v1/tasks} to
 * create tasks at all. This is safe -- {@link IntegrationScopeFilter} is a servlet filter and so
 * runs before the role check ever executes; it already denies every request outside its narrow
 * allow-list (task create/read, project/run/approval read, the rejected-sender audit hook)
 * regardless of role, so the role only matters for the allow-listed endpoints it does let through.
 */
@Component
public class IntegrationServiceAccountSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(IntegrationServiceAccountSeeder.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TalosProperties properties;

	public IntegrationServiceAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
			TalosProperties properties) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		seed(properties.telegramServiceEmail(), properties.telegramServicePassword(), "Telegram integration");
		seed(properties.whatsappServiceEmail(), properties.whatsappServicePassword(), "WhatsApp integration");
	}

	private void seed(String email, String password, String name) {
		if (email == null || email.isBlank() || password == null || password.isBlank()) {
			return;
		}
		// Upgrade path (found live): an install that seeded this account before Phase 15 has it
		// stuck on the old VIEWER default forever, since seeding is otherwise idempotent and never
		// touches an existing row -- without this, task creation from chat silently starts 403ing
		// the moment the API image is upgraded, with no seeder log line to explain why.
		userRepository.findByEmail(email).ifPresentOrElse(existing -> {
			if (existing.getRole() == Role.VIEWER) {
				existing.changeRole(Role.MAINTAINER);
				userRepository.save(existing);
				log.info("Upgraded {} service account {} from VIEWER to MAINTAINER (Phase 15)", name, email);
			}
		}, () -> {
			User account = new User(email, name, passwordEncoder.encode(password), Role.MAINTAINER);
			userRepository.save(account);
			log.info("Seeded {} service account {}", name, email);
		});
	}
}
