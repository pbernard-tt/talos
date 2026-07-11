package dev.talos.secrets;

import dev.talos.common.TalosProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecretServiceTest {

	@Mock
	private SecretValueRepository secretValueRepository;

	private SecretService secretService;

	@BeforeEach
	void setUp() {
		String base64Key = Base64.getEncoder().encodeToString("a-32-byte-test-secrets-key-!!!!!".getBytes());
		secretService = new SecretService(secretValueRepository, new TalosProperties(null, null, null, null,
				base64Key, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
	}

	@Test
	void encryptThenDecrypt_roundTripsToOriginalPlaintext() {
		ArgumentCaptor<SecretValue> saved = ArgumentCaptor.forClass(SecretValue.class);
		when(secretValueRepository.save(saved.capture())).thenAnswer(inv -> saved.getValue());

		secretService.encrypt("ghp_super-secret-token");
		SecretValue persisted = saved.getValue();
		when(secretValueRepository.findById(persisted.getId())).thenReturn(Optional.of(persisted));

		assertThat(secretService.decrypt(persisted.getId())).isEqualTo("ghp_super-secret-token");
	}

	@Test
	void encryptTwice_producesDistinctCiphertextAndNonce() {
		ArgumentCaptor<SecretValue> saved = ArgumentCaptor.forClass(SecretValue.class);
		when(secretValueRepository.save(saved.capture())).thenAnswer(inv -> saved.getValue());

		secretService.encrypt("same-plaintext");
		SecretValue first = saved.getValue();
		secretService.encrypt("same-plaintext");
		SecretValue second = saved.getValue();

		assertThat(first.getNonce()).isNotEqualTo(second.getNonce());
		assertThat(first.getEncryptedValue()).isNotEqualTo(second.getEncryptedValue());
	}

	@Test
	void decrypt_unknownSecretRef_throws() {
		UUID missing = UUID.randomUUID();
		when(secretValueRepository.findById(missing)).thenReturn(Optional.empty());

		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> secretService.decrypt(missing));
	}
}
