package dev.talos.secrets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IntegrationCredentialRepository extends JpaRepository<IntegrationCredential, UUID> {
	List<IntegrationCredential> findByIntegrationId(UUID integrationId);
}
