package dev.talos.integrations;

import dev.talos.audit.AuditService;
import dev.talos.events.EventPublisher;
import dev.talos.integrations.dto.DeployEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 10's "deploy status polling" task, mirroring {@code dev.talos.runs.RunReaper}'s
 * @Scheduled/direct-call-in-test style: a Dokploy build+deploy can run well past a single HTTP
 * request, so this polls every in-flight environment instead of blocking the trigger request.
 */
@Component
public class DeployStatusPoller {

	private static final Logger log = LoggerFactory.getLogger(DeployStatusPoller.class);

	private final ProjectEnvironmentRepository projectEnvironmentRepository;
	private final IntegrationService integrationService;
	private final DeployProvider deployProvider;
	private final AuditService auditService;
	private final EventPublisher eventPublisher;

	public DeployStatusPoller(ProjectEnvironmentRepository projectEnvironmentRepository,
			IntegrationService integrationService, DeployProvider deployProvider, AuditService auditService,
			EventPublisher eventPublisher) {
		this.projectEnvironmentRepository = projectEnvironmentRepository;
		this.integrationService = integrationService;
		this.deployProvider = deployProvider;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	@Scheduled(fixedDelay = 30000)
	public void pollInFlightDeploys() {
		List<ProjectEnvironment> inFlight = projectEnvironmentRepository.findByLastDeployStatus(DeployStatus.RUNNING);
		for (ProjectEnvironment env : inFlight) {
			try {
				poll(env);
			} catch (RuntimeException e) {
				log.error("Deploy status poll failed for project_environment {}", env.getId(), e);
			}
		}
	}

	private void poll(ProjectEnvironment env) {
		IntegrationService.DokployCredentials credentials = integrationService.resolveDokployCredentials();
		DeployProvider.DeployPollResult result = deployProvider.pollLatestStatus(credentials.baseUrl(),
				credentials.token(), env.getAppId());
		if (!result.terminal()) {
			return;
		}

		env.markTerminal(result.status());
		projectEnvironmentRepository.save(env);

		auditService.record(null, "run.deploy." + result.status().name().toLowerCase(Locale.ROOT), "run",
				env.getLastRunId(), Map.of("environment", env.getEnvironment(), "appId", env.getAppId()));

		String eventType = result.status() == DeployStatus.SUCCEEDED ? "deploy.completed" : "deploy.failed";
		eventPublisher.publish(eventType, new DeployEventPayload(env.getLastRunId(), env.getProjectId(),
				env.getEnvironment(), env.getAppId(), result.errorMessage()));
	}
}
