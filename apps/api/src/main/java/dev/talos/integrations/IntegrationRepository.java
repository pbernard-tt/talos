package dev.talos.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IntegrationRepository extends JpaRepository<Integration, UUID> {
}
