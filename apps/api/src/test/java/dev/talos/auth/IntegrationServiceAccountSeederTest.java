package dev.talos.auth;

import dev.talos.common.TalosProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Section 16 Phase 15: an install that seeded these accounts before Phase 15 must have them
 * upgraded from the old VIEWER default to MAINTAINER, not left stuck (found live -- see phase
 * report's root-cause section). */
@ExtendWith(MockitoExtension.class)
class IntegrationServiceAccountSeederTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private TalosProperties properties;
	@Mock
	private ApplicationArguments args;

	private IntegrationServiceAccountSeeder seeder;

	@BeforeEach
	void setUp() {
		seeder = new IntegrationServiceAccountSeeder(userRepository, passwordEncoder, properties);
		when(properties.whatsappServiceEmail()).thenReturn(null);
		when(properties.whatsappServicePassword()).thenReturn(null);
	}

	@Test
	void existingViewerAccount_isUpgradedToMaintainer() {
		when(properties.telegramServiceEmail()).thenReturn("telegram-bot@talos.local");
		when(properties.telegramServicePassword()).thenReturn("some-password");
		User existing = new User("telegram-bot@talos.local", "Telegram integration", "hash", Role.VIEWER);
		when(userRepository.findByEmail("telegram-bot@talos.local")).thenReturn(Optional.of(existing));

		seeder.run(args);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertThat(saved.getValue().getRole()).isEqualTo(Role.MAINTAINER);
	}

	@Test
	void existingMaintainerAccount_isLeftAlone() {
		when(properties.telegramServiceEmail()).thenReturn("telegram-bot@talos.local");
		when(properties.telegramServicePassword()).thenReturn("some-password");
		User existing = new User("telegram-bot@talos.local", "Telegram integration", "hash", Role.MAINTAINER);
		when(userRepository.findByEmail("telegram-bot@talos.local")).thenReturn(Optional.of(existing));

		seeder.run(args);

		verify(userRepository, never()).save(any());
	}

	@Test
	void noExistingAccount_isSeededFreshAsMaintainer() {
		when(properties.telegramServiceEmail()).thenReturn("telegram-bot@talos.local");
		when(properties.telegramServicePassword()).thenReturn("some-password");
		when(userRepository.findByEmail("telegram-bot@talos.local")).thenReturn(Optional.empty());
		when(passwordEncoder.encode(eq("some-password"))).thenReturn("hashed");

		seeder.run(args);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertThat(saved.getValue().getRole()).isEqualTo(Role.MAINTAINER);
		assertThat(saved.getValue().getEmail()).isEqualTo("telegram-bot@talos.local");
	}
}
