package dev.talos.auth;

import dev.talos.common.TalosProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Seeds the single MVP admin user from TALOS_ADMIN_EMAIL/TALOS_ADMIN_PASSWORD (Section 12.2). Idempotent. */
@Component
public class AdminSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TalosProperties properties;

	public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, TalosProperties properties) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		String email = properties.adminEmail();
		String password = properties.adminPassword();
		if (email == null || email.isBlank() || password == null || password.isBlank()) {
			log.warn("TALOS_ADMIN_EMAIL/TALOS_ADMIN_PASSWORD not set; skipping admin seed");
			return;
		}
		if (userRepository.existsByEmail(email)) {
			return;
		}
		User admin = new User(email, "Admin", passwordEncoder.encode(password), Role.OWNER);
		userRepository.save(admin);
		log.info("Seeded admin user {}", email);
	}
}
