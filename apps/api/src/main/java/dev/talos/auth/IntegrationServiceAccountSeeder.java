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
 * Role is VIEWER -- the closest existing Section 9.3 role to "least privilege" -- but the real
 * enforcement is {@link IntegrationScopeFilter}, keyed off the email match in {@link JwtService},
 * not this role (RBAC enforcement itself is still Phase 15).
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
		if (userRepository.existsByEmail(email)) {
			return;
		}
		User account = new User(email, name, passwordEncoder.encode(password), Role.VIEWER);
		userRepository.save(account);
		log.info("Seeded {} service account {}", name, email);
	}
}
