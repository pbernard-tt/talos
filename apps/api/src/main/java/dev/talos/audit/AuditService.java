package dev.talos.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Write-only audit trail (Section 12.2): task creation, prompts, transitions, approvals, pushes, deploys, etc. */
@Service
public class AuditService {

	private final AuditEventRepository repository;

	public AuditService(AuditEventRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public AuditEvent record(UUID actorUserId, String eventType, String entityType, UUID entityId,
			Map<String, Object> details) {
		AuditEvent event = new AuditEvent(actorUserId, eventType, entityType, entityId, details);
		return repository.save(event);
	}
}
