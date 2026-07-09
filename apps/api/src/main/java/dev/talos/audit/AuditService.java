package dev.talos.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Write-only audit trail (Section 12.2): task creation, prompts, transitions, approvals, pushes,
 * deploys, etc. REQUIRES_NEW so a rejected-transition audit row (recorded just before the caller
 * throws to reject the request) survives that caller's transaction rolling back -- the audit
 * trail must record what was attempted, not just what committed.
 */
@Service
public class AuditService {

	private final AuditEventRepository repository;

	public AuditService(AuditEventRepository repository) {
		this.repository = repository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public AuditEvent record(UUID actorUserId, String eventType, String entityType, UUID entityId,
			Map<String, Object> details) {
		AuditEvent event = new AuditEvent(actorUserId, eventType, entityType, entityId, details);
		return repository.save(event);
	}
}
