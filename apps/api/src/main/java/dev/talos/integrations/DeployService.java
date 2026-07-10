package dev.talos.integrations;

import dev.talos.approvals.Approval;
import dev.talos.approvals.ApprovalRepository;
import dev.talos.approvals.dto.ApprovalRequestedPayload;
import dev.talos.audit.AuditService;
import dev.talos.common.ApiException;
import dev.talos.events.EventPublisher;
import dev.talos.integrations.dto.DeployEventPayload;
import dev.talos.projects.ProjectConfig;
import dev.talos.projects.ProjectConfigRepository;
import dev.talos.runs.AgentRun;
import dev.talos.runs.RunStatus;
import dev.talos.tasks.Task;
import dev.talos.tasks.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Section 8.1's final step for a deploy-configured project (Phase 10): gates a COMPLETED run's
 * deploy behind a DEPLOY-type approval whenever the target is production or talos.yaml requires
 * it (Section 12.2), then triggers Dokploy and lets {@link DeployStatusPoller} resolve it.
 */
@Service
public class DeployService {

	private final ProjectConfigRepository projectConfigRepository;
	private final ProjectEnvironmentRepository projectEnvironmentRepository;
	private final ApprovalRepository approvalRepository;
	private final TaskRepository taskRepository;
	private final IntegrationService integrationService;
	private final DeployProvider deployProvider;
	private final AuditService auditService;
	private final EventPublisher eventPublisher;

	public DeployService(ProjectConfigRepository projectConfigRepository,
			ProjectEnvironmentRepository projectEnvironmentRepository, ApprovalRepository approvalRepository,
			TaskRepository taskRepository, IntegrationService integrationService, DeployProvider deployProvider,
			AuditService auditService, EventPublisher eventPublisher) {
		this.projectConfigRepository = projectConfigRepository;
		this.projectEnvironmentRepository = projectEnvironmentRepository;
		this.approvalRepository = approvalRepository;
		this.taskRepository = taskRepository;
		this.integrationService = integrationService;
		this.deployProvider = deployProvider;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	public record DeployRequestResult(boolean approvalRequired, Approval approval, ProjectEnvironment environment) {
	}

	private record DeployConfig(String provider, String appId, String environment, boolean approvalRequired) {
	}

	/** GET /runs/{id}/deploy: re-resolves the deploy: block the same way requestDeploy does, but read-only. */
	public ProjectEnvironment getStatus(AgentRun run) {
		DeployConfig deployConfig = resolveDeployBlock(run);
		return projectEnvironmentRepository
				.findByProjectIdAndEnvironment(run.getProjectId(), deployConfig.environment())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEPLOY_NEVER_REQUESTED",
						"No deploy has been requested for run %s yet".formatted(run.getId())));
	}

	/**
	 * Deliberately NOT @Transactional (see triggerNow's javadoc): the no-approval-required branch
	 * calls triggerNow inline, and wrapping this whole method would let a provider failure roll
	 * back the ProjectEnvironment row this method itself just created/synced.
	 */
	public DeployRequestResult requestDeploy(AgentRun run, UUID actorUserId) {
		if (run.getStatus() != RunStatus.COMPLETED) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "DEPLOY_REQUIRES_COMPLETED_RUN",
					"Run %s is not COMPLETED (currently %s) -- only a completed run's code can be deployed"
							.formatted(run.getId(), run.getStatus()));
		}
		DeployConfig deployConfig = resolveDeployBlock(run);

		ProjectEnvironment env = projectEnvironmentRepository
				.findByProjectIdAndEnvironment(run.getProjectId(), deployConfig.environment())
				.orElseGet(() -> new ProjectEnvironment(run.getProjectId(), deployConfig.environment(),
						deployConfig.provider(), deployConfig.appId(), deployConfig.approvalRequired()));
		env.syncFromConfig(deployConfig.provider(), deployConfig.appId(), deployConfig.approvalRequired());
		env = projectEnvironmentRepository.save(env);

		// Section 12.2: production deploys always require approval, regardless of talos.yaml.
		boolean approvalRequired = "production".equals(deployConfig.environment()) || deployConfig.approvalRequired();
		if (approvalRequired) {
			Task task = taskRepository.findById(run.getTaskId())
					.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found"));
			Approval approval = new Approval(run.getTaskId(), run.getId(), "DEPLOY",
					"Deploy to " + deployConfig.environment(), actorUserId, Instant.now().plus(Duration.ofHours(24)),
					deployConfig.environment());
			approval = approvalRepository.save(approval);
			auditService.record(actorUserId, "deploy.approval.requested", "run", run.getId(),
					Map.of("environment", deployConfig.environment(), "appId", deployConfig.appId()));
			eventPublisher.publish("approval.requested",
					new ApprovalRequestedPayload(approval.getId(), run.getId(), task.getTitle(),
							run.getReviewStatus().name()));
			return new DeployRequestResult(true, approval, env);
		}

		triggerNow(env, run, actorUserId);
		return new DeployRequestResult(false, null, env);
	}

	private DeployConfig resolveDeployBlock(AgentRun run) {
		ProjectConfig config = projectConfigRepository.findByProjectIdAndActiveTrue(run.getProjectId())
				.orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_ACTIVE_CONFIG",
						"Project has no active talos.yaml"));
		Map<String, Object> deployBlock = asMap(config.getParsedJson().get("deploy"));
		String provider = stringOrDefault(deployBlock, "provider", "dokploy");
		String appId = stringOrDefault(deployBlock, "app_id", null);
		String environment = stringOrDefault(deployBlock, "environment", null);
		boolean approvalRequiredInConfig = booleanOrDefault(deployBlock, "approval_required", true);
		if (appId == null || environment == null) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_DEPLOY_CONFIGURED",
					"Project's talos.yaml has no deploy.app_id/deploy.environment configured");
		}
		return new DeployConfig(provider, appId, environment, approvalRequiredInConfig);
	}

	/**
	 * Called either directly (no approval required) or by ApprovalService when a DEPLOY approval is
	 * approved. Deliberately NOT @Transactional: deployProvider.trigger is an external HTTP call,
	 * and if it throws, an enclosing transaction would roll back the very "mark FAILED" write this
	 * method makes in its catch block. Each repository .save() below already runs in its own
	 * transaction (Spring Data's generated repository methods are @Transactional themselves), so
	 * splitting this into two independent writes -- one per branch -- is both correct and simpler
	 * than a REQUIRES_NEW workaround.
	 */
	public void triggerNow(ProjectEnvironment env, AgentRun run, UUID actorUserId) {
		IntegrationService.DokployCredentials credentials = integrationService.resolveDokployCredentials();
		String title = "talos: deploy run %s to %s".formatted(run.getId(), env.getEnvironment());
		try {
			deployProvider.trigger(credentials.baseUrl(), credentials.token(), env.getAppId(), title);
		} catch (RuntimeException e) {
			env.markTriggered(run.getId());
			env.markTerminal(DeployStatus.FAILED);
			projectEnvironmentRepository.save(env);
			eventPublisher.publish("deploy.failed", new DeployEventPayload(run.getId(), run.getProjectId(),
					env.getEnvironment(), env.getAppId(), e.getMessage()));
			throw e;
		}

		env.markTriggered(run.getId());
		projectEnvironmentRepository.save(env);
		auditService.record(actorUserId, "run.deploy.triggered", "run", run.getId(),
				Map.of("environment", env.getEnvironment(), "appId", env.getAppId()));
		eventPublisher.publish("deploy.requested",
				new DeployEventPayload(run.getId(), run.getProjectId(), env.getEnvironment(), env.getAppId(), null));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
	}

	private static String stringOrDefault(Map<String, Object> map, String key, String defaultValue) {
		Object value = map.get(key);
		return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : defaultValue;
	}

	private static boolean booleanOrDefault(Map<String, Object> map, String key, boolean defaultValue) {
		Object value = map.get(key);
		return value instanceof Boolean booleanValue ? booleanValue : defaultValue;
	}
}
