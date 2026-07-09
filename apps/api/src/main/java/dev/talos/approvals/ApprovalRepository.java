package dev.talos.approvals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {
	List<Approval> findByRunId(UUID runId);

	List<Approval> findByStatus(ApprovalStatus status);
}
