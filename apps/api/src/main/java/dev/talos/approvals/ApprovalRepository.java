package dev.talos.approvals;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {
	/** Section 10.2's GET /approvals filters: each of status/runId/approvalType is optional. */
	@Query("""
			SELECT a FROM Approval a
			WHERE (:status IS NULL OR a.status = :status)
			  AND (:runId IS NULL OR a.runId = :runId)
			  AND (:approvalType IS NULL OR a.approvalType = :approvalType)
			""")
	Page<Approval> search(@Param("status") ApprovalStatus status, @Param("runId") UUID runId,
			@Param("approvalType") String approvalType, Pageable pageable);
}
