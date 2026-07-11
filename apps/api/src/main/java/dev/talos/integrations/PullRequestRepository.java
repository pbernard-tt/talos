package dev.talos.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PullRequestRepository extends JpaRepository<PullRequest, UUID> {
	List<PullRequest> findByRunId(UUID runId);

	List<PullRequest> findByRunIdInAndStatus(Collection<UUID> runIds, PullRequestStatus status);

	List<PullRequest> findByProviderAndUrl(String provider, String url);
}
