package dev.talos.runs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Section 8.2: "A scheduled reaper in dev.talos.runs (every 60 s) fails runs whose timeout_at has
 * passed and releases their Redis locks." No Redis lock is acquired by anything yet -- the
 * orchestrator (Phase 6) is what takes talos:lock:run:{project_id}:{branch} -- so there is nothing
 * to release here yet; noted in the phase report rather than left unexplained.
 */
@Component
public class RunReaper {

	private static final Logger log = LoggerFactory.getLogger(RunReaper.class);
	private static final String TIMEOUT_MESSAGE = "TIMEOUT";

	private final AgentRunRepository agentRunRepository;
	private final RunService runService;

	public RunReaper(AgentRunRepository agentRunRepository, RunService runService) {
		this.agentRunRepository = agentRunRepository;
		this.runService = runService;
	}

	@Scheduled(fixedDelay = 60000)
	public void reapExpiredRuns() {
		List<AgentRun> expired = agentRunRepository
				.findByStatusInAndTimeoutAtBefore(RunTransitionValidator.reapableStatuses(), Instant.now());
		for (AgentRun run : expired) {
			try {
				runService.transitionRun(run, RunStatus.FAILED, TIMEOUT_MESSAGE, null);
			} catch (RuntimeException e) {
				log.error("Reaper failed to fail expired run {}", run.getId(), e);
			}
		}
	}
}
