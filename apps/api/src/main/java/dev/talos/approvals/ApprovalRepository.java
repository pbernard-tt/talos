package dev.talos.approvals;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {
	List<Approval> findByRunId(UUID runId);

	List<Approval> findByStatus(ApprovalStatus status);

	Page<Approval> findByStatus(ApprovalStatus status, Pageable pageable);

	Page<Approval> findByRunId(UUID runId, Pageable pageable);

	Page<Approval> findByStatusAndRunId(ApprovalStatus status, UUID runId, Pageable pageable);
}
