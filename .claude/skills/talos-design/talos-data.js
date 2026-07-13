// Talos mock data boundary.
// This module stands in for the generated OpenAPI client + domain stores described in the
// implementation plan (ProjectStore, TaskStore, RunStore, ReviewStore, DeploymentStore,
// IntegrationStore, MemoryStore, CostStore, TeamStore, AuditStore, SystemHealthStore).
// Every export here is shaped the way the real REST responses are shaped (Section 9 DDL /
// Section 10 API surface) so swapping this file for real HTTP calls is a boundary change,
// not a component rewrite.

export const NOW = new Date('2026-07-12T16:40:00Z').getTime();

const minutesAgo = (m) => new Date(NOW - m * 60000).toISOString();
const daysAgo = (d) => new Date(NOW - d * 86400000).toISOString();

export const CURRENT_USER = {
  id: 'u-1', name: 'Paul Bernard', email: 'paul@ledgerly.dev', role: 'OWNER', initials: 'PB'
};

export const PROJECTS = [
  {
    id: 'p-ledgerly-api', slug: 'ledgerly-api', name: 'Ledgerly API', description: 'Core ledger, invoicing and billing service.',
    repoUrl: 'github.com/paulbernard/ledgerly-api', defaultBranch: 'main', stackType: 'spring-boot',
    status: 'ACTIVE', memoryEnabled: true, agentDefault: 'claude-code',
    buildCommand: './mvnw clean package -DskipTests=false', testCommand: './mvnw test',
    deployAppId: 'ledgerly-api-prod', integrationHealth: 'healthy', testHealth: 'passing',
    lastDeployStatus: 'SUCCEEDED', lastDeployedAt: minutesAgo(320), openTasks: 7
  },
  {
    id: 'p-ledgerly-web', slug: 'ledgerly-web', name: 'Ledgerly Web', description: 'Customer-facing billing dashboard.',
    repoUrl: 'github.com/paulbernard/ledgerly-web', defaultBranch: 'main', stackType: 'angular',
    status: 'ACTIVE', memoryEnabled: true, agentDefault: 'claude-code',
    buildCommand: 'ng build', testCommand: 'ng test --watch=false',
    deployAppId: 'ledgerly-web-prod', integrationHealth: 'healthy', testHealth: 'passing',
    lastDeployStatus: 'SUCCEEDED', lastDeployedAt: minutesAgo(900), openTasks: 5
  },
  {
    id: 'p-pulse-analytics', slug: 'pulse-analytics', name: 'Pulse Analytics', description: 'Usage analytics and reporting pipeline.',
    repoUrl: 'github.com/paulbernard/pulse-analytics', defaultBranch: 'main', stackType: 'python',
    status: 'ACTIVE', memoryEnabled: true, agentDefault: 'codex-cli',
    buildCommand: 'uv build', testCommand: 'uv run pytest',
    deployAppId: 'pulse-analytics-prod', integrationHealth: 'degraded', testHealth: 'failing',
    lastDeployStatus: 'FAILED', lastDeployedAt: minutesAgo(2600), openTasks: 6
  },
  {
    id: 'p-sendwave', slug: 'sendwave', name: 'Sendwave', description: 'Transactional email and notification service.',
    repoUrl: 'github.com/paulbernard/sendwave', defaultBranch: 'main', stackType: 'node',
    status: 'ACTIVE', memoryEnabled: false, agentDefault: 'claude-code',
    buildCommand: 'npm run build', testCommand: 'npm test',
    deployAppId: 'sendwave-prod', integrationHealth: 'healthy', testHealth: 'passing',
    lastDeployStatus: 'SUCCEEDED', lastDeployedAt: daysAgo(2), openTasks: 4
  },
  {
    id: 'p-docsy', slug: 'docsy', name: 'Docsy', description: 'API documentation generation and hosting.',
    repoUrl: 'github.com/paulbernard/docsy', defaultBranch: 'main', stackType: 'node',
    status: 'ACTIVE', memoryEnabled: true, agentDefault: 'claude-code',
    buildCommand: 'npm run build', testCommand: 'npm test',
    deployAppId: 'docsy-prod', integrationHealth: 'healthy', testHealth: 'passing',
    lastDeployStatus: 'SUCCEEDED', lastDeployedAt: daysAgo(6), openTasks: 3
  },
  {
    id: 'p-statuscast', slug: 'statuscast', name: 'Statuscast', description: 'Public status page for all products.',
    repoUrl: 'github.com/paulbernard/statuscast', defaultBranch: 'main', stackType: 'static',
    status: 'ACTIVE', memoryEnabled: false, agentDefault: 'claude-code',
    buildCommand: 'npm run build', testCommand: 'npm run lint',
    deployAppId: 'statuscast-prod', integrationHealth: 'action-required', testHealth: 'unknown',
    lastDeployStatus: 'SUCCEEDED', lastDeployedAt: daysAgo(14), openTasks: 2
  }
];

let taskSeq = 1;
const T = (projectId, title, status, extra) => ({
  id: 't-' + (taskSeq++), projectId, title, status, priority: 'MEDIUM', riskLevel: 'NORMAL',
  source: 'DASHBOARD', assignedAgentKey: 'claude-code', labels: [], ...extra
});

export const TASKS = [
  T('p-ledgerly-api', 'Add idempotency keys to POST /invoices', 'RUNNING', { priority: 'HIGH', labels: ['api', 'reliability'], branch: 'agent/task-8-idempotency-keys', source: 'DASHBOARD' }),
  T('p-ledgerly-api', 'Fix rounding error in tax line calculation', 'REVIEW', { priority: 'HIGH', riskLevel: 'HIGH', labels: ['billing', 'bugfix'], branch: 'agent/task-9-tax-rounding' }),
  T('p-ledgerly-api', 'Add pagination to GET /customers', 'READY', { labels: ['api'] }),
  T('p-ledgerly-api', 'Upgrade Spring Boot to 4.1.2', 'BLOCKED', { blockedReason: 'Waiting on Pulse Analytics client compatibility check', labels: ['deps'] }),
  T('p-ledgerly-api', 'Write architecture doc for webhook retries', 'BACKLOG', { labels: ['docs'] }),
  T('p-ledgerly-api', 'Add refund reversal endpoint', 'DONE', { labels: ['api'] }),
  T('p-ledgerly-api', 'Remove deprecated /v0 endpoints', 'CANCELLED', { labels: ['cleanup'] }),
  T('p-ledgerly-web', 'Invoice detail page: add PDF download', 'RUNNING', { labels: ['ui'], branch: 'agent/task-16-invoice-pdf', assignedAgentKey: 'codex-cli' }),
  T('p-ledgerly-web', 'Dark-mode contrast pass on billing table', 'READY', { labels: ['ui', 'a11y'] }),
  T('p-ledgerly-web', 'Fix flaky checkout e2e test', 'BLOCKED', { blockedReason: 'Run timed out twice waiting on test container startup', labels: ['tests'] }),
  T('p-ledgerly-web', 'Add empty state to reports tab', 'BACKLOG', { labels: ['ui'] }),
  T('p-ledgerly-web', 'Wire up new /customers pagination', 'DONE', { labels: ['ui'] }),
  T('p-pulse-analytics', 'Fix nightly aggregation job double-counting', 'RUNNING', { priority: 'HIGH', riskLevel: 'HIGH', labels: ['pipeline', 'bugfix'], branch: 'agent/task-23-agg-doublecount', assignedAgentKey: 'codex-cli' }),
  T('p-pulse-analytics', 'Add cohort retention report', 'REVIEW', { labels: ['reporting'] }),
  T('p-pulse-analytics', 'Migrate to pandas 3.x', 'BLOCKED', { blockedReason: '3 consecutive test failures in transform pipeline', labels: ['deps'] }),
  T('p-pulse-analytics', 'Add data quality alerting', 'READY', { labels: ['ops'] }),
  T('p-pulse-analytics', 'Backfill Q2 revenue attribution', 'BACKLOG', { priority: 'HIGH', labels: ['data'] }),
  T('p-pulse-analytics', 'Document warehouse schema', 'DONE', { labels: ['docs'] }),
  T('p-sendwave', 'Add WhatsApp delivery receipts webhook', 'READY', { labels: ['integrations'] }),
  T('p-sendwave', 'Reduce bounce-handling latency', 'REVIEW', { labels: ['perf'] }),
  T('p-sendwave', 'Add template preview endpoint', 'BACKLOG', { labels: ['api'] }),
  T('p-sendwave', 'Rotate SMTP provider credentials', 'DONE', { labels: ['ops'] }),
  T('p-docsy', 'Generate OpenAPI docs for internal API', 'RUNNING', { labels: ['docs'], branch: 'agent/task-31-openapi-docs' }),
  T('p-docsy', 'Fix broken anchor links after theme update', 'READY', { labels: ['bugfix'] }),
  T('p-docsy', 'Add versioned docs support', 'BACKLOG', { labels: ['feature'] }),
  T('p-statuscast', 'Add historical uptime chart', 'READY', { labels: ['feature'] }),
  T('p-statuscast', 'Fix incident timeline timezone bug', 'BLOCKED', { blockedReason: 'GitHub integration degraded \u2014 cannot open PR', labels: ['bugfix'] }),
  T('p-statuscast', 'Add subscribe-by-email', 'BACKLOG', { labels: ['feature'] })
];

let runSeq = 1;
const R = (taskId, projectId, status, extra) => ({
  id: 'r-' + (runSeq++), taskId, projectId, status, agentKey: 'claude-code',
  providerAuthMode: 'subscription_local', testStatus: 'NOT_RUN', reviewStatus: 'CLEAN', ...extra
});

export const RUNS = [
  R('t-1', 'p-ledgerly-api', 'RUNNING_AGENT', {
    branchName: 'agent/task-8-idempotency-keys', currentStep: 'AGENT', startedAt: minutesAgo(6),
    timeoutAt: minutesAgo(-24), logsStreaming: true, attentionRequired: false, agentKey: 'claude-code',
    providerAuthMode: 'subscription_local'
  }),
  R('t-8', 'p-ledgerly-web', 'RUNNING_TESTS', {
    branchName: 'agent/task-16-invoice-pdf', currentStep: 'TESTS', startedAt: minutesAgo(11),
    timeoutAt: minutesAgo(-9), logsStreaming: true, attentionRequired: false, agentKey: 'codex-cli',
    providerAuthMode: 'api_key', tokenUsage: { input: 18400, output: 5200 }, costUsd: 0.61
  }),
  R('t-13', 'p-pulse-analytics', 'PREPARING_WORKSPACE', {
    branchName: 'agent/task-23-agg-doublecount', currentStep: 'WORKSPACE', startedAt: minutesAgo(1),
    timeoutAt: minutesAgo(-4), logsStreaming: true, attentionRequired: false, agentKey: 'codex-cli',
    providerAuthMode: 'api_key'
  }),
  R('t-2', 'p-ledgerly-api', 'WAITING_APPROVAL', {
    branchName: 'agent/task-9-tax-rounding', currentStep: 'REVIEW', startedAt: minutesAgo(48),
    completedAt: minutesAgo(31), testStatus: 'PASSED', reviewStatus: 'CLEAN', agentKey: 'claude-code',
    providerAuthMode: 'subscription_local', summary: 'Fixed tax line rounding to use half-even banker\u2019s rounding at the cent boundary; added 6 unit tests covering currency edge cases.',
    changedFiles: 4, additions: 86, deletions: 12, attentionRequired: true
  }),
  R('t-14', 'p-pulse-analytics', 'WAITING_APPROVAL', {
    branchName: 'agent/task-24-cohort-retention', currentStep: 'REVIEW', startedAt: minutesAgo(140),
    completedAt: minutesAgo(95), testStatus: 'PASSED', reviewStatus: 'RISK_FLAGGED', agentKey: 'codex-cli',
    providerAuthMode: 'api_key', tokenUsage: { input: 41200, output: 12800 }, costUsd: 1.94,
    summary: 'Added a cohort retention report endpoint and materialized view refresh job.',
    changedFiles: 9, additions: 340, deletions: 18, attentionRequired: true,
    matchedPatterns: ['**/migrations/**']
  }),
  R('t-20', 'p-sendwave', 'WAITING_APPROVAL', {
    branchName: 'agent/task-42-bounce-latency', currentStep: 'REVIEW', startedAt: minutesAgo(260),
    completedAt: minutesAgo(210), testStatus: 'PASSED', reviewStatus: 'CLEAN', agentKey: 'claude-code',
    providerAuthMode: 'subscription_local', summary: 'Moved bounce classification to a queue consumer instead of inline webhook handling, cutting p95 handling latency from 4.1s to 380ms.',
    changedFiles: 6, additions: 154, deletions: 61, attentionRequired: true
  }),
  R('t-23', 'p-docsy', 'RUNNING_AGENT', {
    branchName: 'agent/task-31-openapi-docs', currentStep: 'AGENT', startedAt: minutesAgo(3),
    timeoutAt: minutesAgo(-27), logsStreaming: true, attentionRequired: false, agentKey: 'claude-code',
    providerAuthMode: 'subscription_local'
  }),
  R('t-4', 'p-ledgerly-api', 'FAILED', {
    branchName: 'agent/task-11-springboot-upgrade', currentStep: 'TESTS', startedAt: minutesAgo(1440 + 40),
    completedAt: daysAgo(1), testStatus: 'FAILED', agentKey: 'claude-code', providerAuthMode: 'subscription_local',
    errorMessage: '14 tests failed after dependency bump \u2014 incompatible Jackson serialization defaults.',
    attentionRequired: true
  }),
  R('t-15', 'p-pulse-analytics', 'FAILED', {
    branchName: 'agent/task-25-pandas3-migration', currentStep: 'TESTS', startedAt: minutesAgo(2880 + 35),
    completedAt: daysAgo(2), testStatus: 'FAILED', agentKey: 'codex-cli', providerAuthMode: 'api_key',
    errorMessage: 'Transform pipeline raised on pandas.DataFrame.applymap removal (3rd consecutive failure).',
    tokenUsage: { input: 29500, output: 8100 }, costUsd: 1.12, attentionRequired: true
  }),
  R('t-6', 'p-ledgerly-api', 'COMPLETED', {
    branchName: 'agent/task-6-refund-reversal', currentStep: 'DEPLOY', startedAt: minutesAgo(4320 + 24),
    completedAt: daysAgo(3), testStatus: 'PASSED', reviewStatus: 'CLEAN', agentKey: 'claude-code',
    providerAuthMode: 'subscription_local', changedFiles: 5, additions: 122, deletions: 8
  }),
  R('t-24', 'p-statuscast', 'CANCELLED', {
    branchName: 'agent/task-44-subscribe-email', currentStep: 'AGENT', startedAt: minutesAgo(5760 + 8),
    completedAt: daysAgo(4), agentKey: 'claude-code', providerAuthMode: 'subscription_local'
  })
];

export const APPROVALS = RUNS.filter(r => r.status === 'WAITING_APPROVAL').map((r, i) => ({
  id: 'ap-' + (i + 1), runId: r.id, taskId: r.taskId, projectId: r.projectId,
  status: 'PENDING', requestedAt: r.completedAt, approvalType: 'RUN_RESULT'
}));

export const PULL_REQUESTS = [
  { id: 'pr-1', runId: 'r-11', provider: 'github', prNumber: 214, url: 'github.com/paulbernard/ledgerly-api/pull/214', status: 'MERGED', sourceBranch: 'agent/task-6-refund-reversal', targetBranch: 'main', createdAt: daysAgo(3) }
];

export const ENVIRONMENTS = [
  { id: 'env-1', projectId: 'p-ledgerly-api', environment: 'production', appId: 'ledgerly-api-prod', approvalRequired: true, lastDeployStatus: 'SUCCEEDED', lastDeployedAt: minutesAgo(320), commit: 'a91f3c2', triggeredBy: 'Paul Bernard' },
  { id: 'env-2', projectId: 'p-ledgerly-api', environment: 'staging', appId: 'ledgerly-api-staging', approvalRequired: false, lastDeployStatus: 'SUCCEEDED', lastDeployedAt: minutesAgo(40), commit: 'f0c88de', triggeredBy: 'Talos (auto)' },
  { id: 'env-3', projectId: 'p-ledgerly-web', environment: 'production', appId: 'ledgerly-web-prod', approvalRequired: true, lastDeployStatus: 'SUCCEEDED', lastDeployedAt: minutesAgo(900), commit: '3bd21aa', triggeredBy: 'Paul Bernard' },
  { id: 'env-4', projectId: 'p-pulse-analytics', environment: 'production', appId: 'pulse-analytics-prod', approvalRequired: true, lastDeployStatus: 'FAILED', lastDeployedAt: minutesAgo(2600), commit: '77e410b', triggeredBy: 'Paul Bernard' },
  { id: 'env-5', projectId: 'p-sendwave', environment: 'production', appId: 'sendwave-prod', approvalRequired: true, lastDeployStatus: 'SUCCEEDED', lastDeployedAt: daysAgo(2), commit: '9c02f11', triggeredBy: 'Paul Bernard' },
  { id: 'env-6', projectId: 'p-docsy', environment: 'production', appId: 'docsy-prod', approvalRequired: true, lastDeployStatus: 'SUCCEEDED', lastDeployedAt: daysAgo(6), commit: 'e51aa09', triggeredBy: 'Paul Bernard' },
  { id: 'env-7', projectId: 'p-statuscast', environment: 'production', appId: 'statuscast-prod', approvalRequired: true, lastDeployStatus: 'SUCCEEDED', lastDeployedAt: daysAgo(14), commit: '2d19f5c', triggeredBy: 'Paul Bernard' }
];

export const INTEGRATIONS = [
  { id: 'int-github', type: 'github', name: 'GitHub', status: 'connected', authType: 'Fine-grained PAT', mode: 'api_key', lastHealthCheck: minutesAgo(4), lastSuccessfulUse: minutesAgo(31), capabilities: ['Clone', 'Push', 'Pull request', 'Webhook'] },
  { id: 'int-dokploy', type: 'dokploy', name: 'Dokploy', status: 'connected', authType: 'API token', mode: 'api_key', lastHealthCheck: minutesAgo(4), lastSuccessfulUse: minutesAgo(320), capabilities: ['Deploy', 'Status polling', 'Rollback'] },
  { id: 'int-claude', type: 'claude-code', name: 'Claude Code', status: 'connected', authType: 'Personal subscription login', mode: 'subscription_local', lastHealthCheck: minutesAgo(6), lastSuccessfulUse: minutesAgo(3), capabilities: ['Headless execution', 'Streaming events', 'Permission hooks'], personalUseOnly: true },
  { id: 'int-codex', type: 'codex-cli', name: 'OpenAI Codex CLI', status: 'connected', authType: 'API key', mode: 'api_key', lastHealthCheck: minutesAgo(6), lastSuccessfulUse: minutesAgo(1), capabilities: ['Headless execution', 'JSONL events'] },
  { id: 'int-opencode', type: 'opencode', name: 'OpenCode', status: 'action-required', authType: 'Not configured', mode: 'n/a', lastHealthCheck: null, lastSuccessfulUse: null, capabilities: ['Headless execution'], notConfigured: true, phaseNote: 'Adapter arrives in Phase 12' },
  { id: 'int-openhands', type: 'openhands', name: 'OpenHands', status: 'disconnected', authType: 'Not configured', mode: 'n/a', lastHealthCheck: null, lastSuccessfulUse: null, capabilities: ['Sandboxed HTTP/ACP execution'], notConfigured: true, phaseNote: 'Adapter arrives in Phase 12+' },
  { id: 'int-shell', type: 'custom-shell', name: 'Custom Shell', status: 'connected', authType: 'None (local execution)', mode: 'n/a', lastHealthCheck: minutesAgo(4), lastSuccessfulUse: daysAgo(3), capabilities: ['Deterministic scripts', 'Build/test/deploy steps'] },
  { id: 'int-telegram', type: 'telegram', name: 'Telegram', status: 'disconnected', authType: 'Bot token', mode: 'n/a', lastHealthCheck: null, lastSuccessfulUse: null, capabilities: ['Task intake', 'Notifications'], notConfigured: true, phaseNote: 'Arrives in Phase 12, Track B' },
  { id: 'int-whatsapp', type: 'whatsapp', name: 'WhatsApp Business Cloud API', status: 'disconnected', authType: 'Signed webhook', mode: 'n/a', lastHealthCheck: null, lastSuccessfulUse: null, capabilities: ['Task intake', 'Notifications'], notConfigured: true, phaseNote: 'Arrives in Phase 12, Track B' },
  { id: 'int-embedding', type: 'embedding', name: 'Embedding provider', status: 'disconnected', authType: 'API key (BYOK)', mode: 'n/a', lastHealthCheck: null, lastSuccessfulUse: null, capabilities: ['Memory chunk embedding'], notConfigured: true, phaseNote: 'Arrives with Memory in Phase 13' },
  { id: 'int-local-artifacts', type: 'local-storage', name: 'Local artifact storage', status: 'connected', authType: 'Local volume', mode: 'n/a', lastHealthCheck: minutesAgo(4), lastSuccessfulUse: minutesAgo(3), capabilities: ['Transcripts', 'Diff patches', 'Test reports'] },
  { id: 'int-minio', type: 'minio', name: 'MinIO', status: 'disconnected', authType: 'S3-compatible credentials', mode: 'n/a', lastHealthCheck: null, lastSuccessfulUse: null, capabilities: ['Object storage for artifacts'], notConfigured: true, phaseNote: 'Arrives in Phase 16' }
];

export const TEAM = [
  { id: 'u-1', name: 'Paul Bernard', email: 'paul@ledgerly.dev', role: 'OWNER', status: 'active', lastActive: minutesAgo(2), initials: 'PB' },
  { id: 'u-2', name: 'Maya Chen', email: 'maya@contractor.dev', role: 'MAINTAINER', status: 'active', lastActive: minutesAgo(95), initials: 'MC' },
  { id: 'u-3', name: 'Jordan Reyes', email: 'jordan@reyesreview.io', role: 'REVIEWER', status: 'invited', lastActive: null, initials: 'JR' },
  { id: 'u-4', name: 'Accounting Viewer', email: 'books@ledgerly.dev', role: 'VIEWER', status: 'active', lastActive: daysAgo(5), initials: 'AV' }
];

export const AUDIT_EVENTS = [
  { id: 'ae-1', actor: 'Paul Bernard', action: 'approval.approved', resource: 'Run r-11', project: 'Ledgerly API', result: 'success', ip: '41.220.14.8', createdAt: daysAgo(3) },
  { id: 'ae-2', actor: 'Talos (system)', action: 'deploy.completed', resource: 'ledgerly-api-prod', project: 'Ledgerly API', result: 'success', ip: null, createdAt: minutesAgo(320) },
  { id: 'ae-3', actor: 'Talos (system)', action: 'policy.violation.flagged', resource: 'Run r-14', project: 'Pulse Analytics', result: 'flagged', ip: null, createdAt: minutesAgo(95) },
  { id: 'ae-4', actor: 'Paul Bernard', action: 'task.created', resource: 'Task t-13', project: 'Pulse Analytics', result: 'success', ip: '41.220.14.8', createdAt: minutesAgo(120) },
  { id: 'ae-5', actor: 'Paul Bernard', action: 'run.started', resource: 'Run r-3', project: 'Pulse Analytics', result: 'success', ip: '41.220.14.8', createdAt: minutesAgo(1) },
  { id: 'ae-6', actor: 'Talos (system)', action: 'deploy.failed', resource: 'pulse-analytics-prod', project: 'Pulse Analytics', result: 'failure', ip: null, createdAt: minutesAgo(2600) },
  { id: 'ae-7', actor: 'Paul Bernard', action: 'auth.login', resource: 'Session', project: null, result: 'success', ip: '41.220.14.8', createdAt: minutesAgo(340) },
  { id: 'ae-8', actor: 'Unknown sender (chat_id 88213)', action: 'telegram.unauthorized_sender', resource: 'Telegram bot', project: null, result: 'blocked', ip: null, createdAt: daysAgo(1) },
  { id: 'ae-9', actor: 'Talos (system)', action: 'integration.health_check', resource: 'GitHub', project: null, result: 'success', ip: null, createdAt: minutesAgo(4) },
  { id: 'ae-10', actor: 'Paul Bernard', action: 'integration.credential_updated', resource: 'Dokploy', project: null, result: 'success', ip: '41.220.14.8', createdAt: daysAgo(9) }
];

export const SYSTEM_SERVICES = [
  { id: 'talos-web', name: 'talos-web', kind: 'Angular dashboard', status: 'healthy', lastCheck: minutesAgo(1), latencyMs: 41 },
  { id: 'talos-api', name: 'talos-api', kind: 'Spring Boot API', status: 'healthy', lastCheck: minutesAgo(1), latencyMs: 58, activeRuns: 3, concurrencyLimit: 4 },
  { id: 'talos-orchestrator', name: 'talos-orchestrator', kind: 'Python orchestrator', status: 'healthy', lastCheck: minutesAgo(1), latencyMs: 22, activeRuns: 3 },
  { id: 'talos-runner-supervisor', name: 'talos-runner-supervisor', kind: 'Python runner supervisor', status: 'healthy', lastCheck: minutesAgo(1), latencyMs: 34, diskUsagePct: 38 },
  { id: 'postgres', name: 'PostgreSQL', kind: 'Database (17)', status: 'healthy', lastCheck: minutesAgo(1), latencyMs: 3 },
  { id: 'rabbitmq', name: 'RabbitMQ', kind: 'Queue (4.1)', status: 'healthy', lastCheck: minutesAgo(1), latencyMs: 9, queueDepth: 2 },
  { id: 'redis', name: 'Redis', kind: 'Cache / pub-sub (7)', status: 'healthy', lastCheck: minutesAgo(1), latencyMs: 2 },
  { id: 'artifact-storage', name: 'Artifact storage', kind: 'Local volume', status: 'healthy', lastCheck: minutesAgo(1), latencyMs: 4, diskUsagePct: 24 },
  { id: 'telegram-adapter', name: 'Telegram adapter', kind: 'Remote trigger (Phase 12)', status: 'unknown', lastCheck: null, latencyMs: null, notConfigured: true },
  { id: 'whatsapp-adapter', name: 'WhatsApp adapter', kind: 'Remote trigger (Phase 12)', status: 'unknown', lastCheck: null, latencyMs: null, notConfigured: true },
  { id: 'minio', name: 'MinIO', kind: 'Object storage (Phase 16)', status: 'unknown', lastCheck: null, latencyMs: null, notConfigured: true }
];

export const COST_MONTHLY_TREND = [
  { month: 'Feb', apiKeyUsd: 18.2, subscription: true },
  { month: 'Mar', apiKeyUsd: 24.9, subscription: true },
  { month: 'Apr', apiKeyUsd: 31.4, subscription: true },
  { month: 'May', apiKeyUsd: 27.8, subscription: true },
  { month: 'Jun', apiKeyUsd: 39.6, subscription: true },
  { month: 'Jul', apiKeyUsd: 22.1, subscription: true }
];

export const COST_BY_PROVIDER = [
  { agentKey: 'codex-cli', label: 'OpenAI Codex CLI', mode: 'api_key', costUsd: 61.90, tokens: 1284000, runs: 34 },
  { agentKey: 'claude-code', label: 'Claude Code', mode: 'subscription_local', costUsd: null, tokens: 2960000, runs: 58 },
  { agentKey: 'custom-shell', label: 'Custom Shell', mode: 'n/a', costUsd: 0, tokens: 0, runs: 21 }
];

export const COST_BY_PROJECT = [
  { projectId: 'p-ledgerly-api', costUsd: 22.40, runs: 26 },
  { projectId: 'p-pulse-analytics', costUsd: 28.10, runs: 18 },
  { projectId: 'p-ledgerly-web', costUsd: 6.80, runs: 14 },
  { projectId: 'p-sendwave', costUsd: 3.10, runs: 9 },
  { projectId: 'p-docsy', costUsd: 1.50, runs: 6 },
  { projectId: 'p-statuscast', costUsd: 0, runs: 2 }
];

export const COST_RECOMMENDATIONS = [
  { id: 'rec-1', title: 'Codex CLI is the cheapest capable agent for Pulse Analytics test-fix tasks', detail: 'Last 8 runs: Codex CLI avg $0.71/run vs Claude Code avg $1.38/run at comparable pass rate.', dismissed: false },
  { id: 'rec-2', title: 'Repeated review failures in pulse-analytics/transforms/', detail: '3 of the last 4 runs touching this path were rejected or flagged. Consider a smaller task scope or an operator note in Memory.', dismissed: false },
  { id: 'rec-3', title: 'Ledgerly API has 4 days of stale blocked work', detail: '"Upgrade Spring Boot to 4.1.2" has been BLOCKED since Jul 8 waiting on a cross-project check.', dismissed: false }
];

export const MEMORY = {
  'p-ledgerly-api': {
    enabled: true, chunkCount: 412, lastIndexed: minutesAgo(320), tokenBudget: 6000,
    conventions: 'Money is always a BigDecimal scaled to 4 places internally, formatted to 2 at the API boundary. Controllers never touch entities directly \u2014 always go through XService and dto records.',
    decisionLog: [
      { date: daysAgo(40), note: 'Chose banker\u2019s rounding (half-even) for all tax calculations to match most jurisdictions\u2019 requirements.' },
      { date: daysAgo(95), note: 'Rejected event sourcing for the ledger table \u2014 added an audit trigger instead, simpler to reason about at this scale.' }
    ],
    contextDocs: ['docs/architecture.md', 'docs/api-guidelines.md', 'docs/billing-domain-model.md']
  },
  'p-pulse-analytics': {
    enabled: true, chunkCount: 268, lastIndexed: minutesAgo(2600), tokenBudget: 4000,
    conventions: 'All pipeline stages are pure functions over pandas DataFrames; no in-place mutation. Nightly job writes to a staging schema, then swaps views atomically.',
    decisionLog: [
      { date: daysAgo(12), note: 'Deferred pandas 3.x migration after 3 straight test failures in the transform layer \u2014 needs a smaller, scoped task.' }
    ],
    contextDocs: ['docs/warehouse-schema.md', 'docs/pipeline-conventions.md']
  }
};
