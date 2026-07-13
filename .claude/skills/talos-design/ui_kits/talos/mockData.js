// Talos UI kit — mock data (same shape as the product's REST responses).
// Plain classic-script globals: no import/export, shared across all screen
// scripts loaded after this file.

const NOW = new Date('2026-07-12T16:40:00Z').getTime();
const minutesAgo = (m) => new Date(NOW - m * 60000).toISOString();
const daysAgo = (d) => new Date(NOW - d * 86400000).toISOString();
const fmtAgo = (iso) => {
  if (!iso) return '—';
  const diffMs = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diffMs / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return m + 'm ago';
  const h = Math.floor(m / 60);
  if (h < 24) return h + 'h ago';
  return Math.floor(h / 24) + 'd ago';
};
const fmtMoney = (n) => (n === null || n === undefined ? '—' : '$' + n.toFixed(2));
const AGENT_LABELS = { 'claude-code': 'Claude Code', 'codex-cli': 'OpenAI Codex CLI', 'opencode': 'OpenCode', 'openhands': 'OpenHands', 'custom-shell': 'Custom Shell' };

const CURRENT_USER = { id: 'u-1', name: 'Paul Bernard', email: 'paul@ledgerly.dev', role: 'OWNER', initials: 'PB' };

const PROJECTS = [
  { id: 'p-ledgerly-api', name: 'Ledgerly API', description: 'Core ledger, invoicing and billing service.', repoUrl: 'github.com/paulbernard/ledgerly-api', defaultBranch: 'main', stackType: 'spring-boot', testHealth: 'passing', integrationHealth: 'healthy', lastDeployStatus: 'SUCCEEDED', lastDeployedAt: minutesAgo(320), memoryEnabled: true, agentDefault: 'claude-code', testCommand: './mvnw test' },
  { id: 'p-ledgerly-web', name: 'Ledgerly Web', description: 'Customer-facing billing dashboard.', repoUrl: 'github.com/paulbernard/ledgerly-web', defaultBranch: 'main', stackType: 'angular', testHealth: 'passing', integrationHealth: 'healthy', lastDeployStatus: 'SUCCEEDED', lastDeployedAt: minutesAgo(900), memoryEnabled: true, agentDefault: 'claude-code', testCommand: 'ng test --watch=false' },
  { id: 'p-pulse-analytics', name: 'Pulse Analytics', description: 'Usage analytics and reporting pipeline.', repoUrl: 'github.com/paulbernard/pulse-analytics', defaultBranch: 'main', stackType: 'python', testHealth: 'failing', integrationHealth: 'degraded', lastDeployStatus: 'FAILED', lastDeployedAt: minutesAgo(2600), memoryEnabled: true, agentDefault: 'codex-cli', testCommand: 'uv run pytest' },
  { id: 'p-sendwave', name: 'Sendwave', description: 'Transactional email and notification service.', repoUrl: 'github.com/paulbernard/sendwave', defaultBranch: 'main', stackType: 'node', testHealth: 'passing', integrationHealth: 'healthy', lastDeployStatus: 'SUCCEEDED', lastDeployedAt: daysAgo(2), memoryEnabled: false, agentDefault: 'claude-code', testCommand: 'npm test' },
  { id: 'p-docsy', name: 'Docsy', description: 'API documentation generation and hosting.', repoUrl: 'github.com/paulbernard/docsy', defaultBranch: 'main', stackType: 'node', testHealth: 'passing', integrationHealth: 'healthy', lastDeployStatus: 'SUCCEEDED', lastDeployedAt: daysAgo(6), memoryEnabled: true, agentDefault: 'claude-code', testCommand: 'npm test' },
  { id: 'p-statuscast', name: 'Statuscast', description: 'Public status page for all products.', repoUrl: 'github.com/paulbernard/statuscast', defaultBranch: 'main', stackType: 'static', testHealth: 'unknown', integrationHealth: 'action-required', lastDeployStatus: 'SUCCEEDED', lastDeployedAt: daysAgo(14), memoryEnabled: false, agentDefault: 'claude-code', testCommand: 'npm run lint' },
];

let _taskId = 1;
const T = (projectId, title, status, extra) => ({ id: 't-' + _taskId++, projectId, title, status, priority: 'MEDIUM', riskLevel: 'NORMAL', agentKey: 'claude-code', ...extra });
const TASKS = [
  T('p-ledgerly-api', 'Add idempotency keys to POST /invoices', 'RUNNING', { priority: 'HIGH', branch: 'agent/task-8-idempotency-keys' }),
  T('p-ledgerly-api', 'Fix rounding error in tax line calculation', 'REVIEW', { priority: 'HIGH', riskLevel: 'HIGH' }),
  T('p-ledgerly-api', 'Add pagination to GET /customers', 'READY', {}),
  T('p-ledgerly-api', 'Upgrade Spring Boot to 4.1.2', 'BLOCKED', { blockedReason: 'Waiting on Pulse Analytics client compatibility check' }),
  T('p-ledgerly-api', 'Write architecture doc for webhook retries', 'BACKLOG', {}),
  T('p-ledgerly-api', 'Add refund reversal endpoint', 'DONE', {}),
  T('p-ledgerly-web', 'Invoice detail page: add PDF download', 'RUNNING', { agentKey: 'codex-cli', branch: 'agent/task-16-invoice-pdf' }),
  T('p-ledgerly-web', 'Dark-mode contrast pass on billing table', 'READY', {}),
  T('p-ledgerly-web', 'Fix flaky checkout e2e test', 'BLOCKED', { blockedReason: 'Run timed out twice waiting on test container startup' }),
  T('p-pulse-analytics', 'Fix nightly aggregation job double-counting', 'RUNNING', { priority: 'HIGH', riskLevel: 'HIGH', agentKey: 'codex-cli' }),
  T('p-pulse-analytics', 'Add cohort retention report', 'REVIEW', {}),
  T('p-pulse-analytics', 'Migrate to pandas 3.x', 'BLOCKED', { blockedReason: '3 consecutive test failures in transform pipeline' }),
  T('p-sendwave', 'Reduce bounce-handling latency', 'REVIEW', {}),
  T('p-sendwave', 'Add template preview endpoint', 'BACKLOG', {}),
  T('p-docsy', 'Generate OpenAPI docs for internal API', 'RUNNING', {}),
  T('p-statuscast', 'Add historical uptime chart', 'READY', {}),
];

let _runId = 1;
const R = (taskId, projectId, status, extra) => ({ id: 'r-' + _runId++, taskId, projectId, status, agentKey: 'claude-code', providerAuthMode: 'subscription_local', testStatus: 'NOT_RUN', reviewStatus: 'CLEAN', ...extra });
const RUNS = [
  R('t-1', 'p-ledgerly-api', 'RUNNING_AGENT', { branchName: 'agent/task-8-idempotency-keys', currentStep: 'AGENT', startedAt: minutesAgo(6) }),
  R('t-7', 'p-ledgerly-web', 'RUNNING_TESTS', { branchName: 'agent/task-16-invoice-pdf', currentStep: 'TESTS', startedAt: minutesAgo(11), agentKey: 'codex-cli', providerAuthMode: 'api_key', costUsd: 0.61 }),
  R('t-2', 'p-ledgerly-api', 'WAITING_APPROVAL', { branchName: 'agent/task-9-tax-rounding', startedAt: minutesAgo(48), completedAt: minutesAgo(31), testStatus: 'PASSED', reviewStatus: 'CLEAN', summary: 'Fixed tax line rounding to use half-even banker’s rounding at the cent boundary; added 6 unit tests covering currency edge cases.', changedFiles: 3, additions: 86, deletions: 12 }),
  R('t-11', 'p-pulse-analytics', 'WAITING_APPROVAL', { branchName: 'agent/task-24-cohort-retention', startedAt: minutesAgo(140), completedAt: minutesAgo(95), testStatus: 'PASSED', reviewStatus: 'RISK_FLAGGED', agentKey: 'codex-cli', providerAuthMode: 'api_key', costUsd: 1.94, summary: 'Added a cohort retention report endpoint and materialized view refresh job.', changedFiles: 3, additions: 160, deletions: 1, matchedPattern: '**/migrations/**' }),
  R('t-4', 'p-ledgerly-api', 'FAILED', { branchName: 'agent/task-11-springboot-upgrade', startedAt: minutesAgo(1480), completedAt: daysAgo(1), testStatus: 'FAILED', errorMessage: '14 tests failed after dependency bump — incompatible Jackson serialization defaults.' }),
  R('t-6', 'p-ledgerly-api', 'COMPLETED', { branchName: 'agent/task-6-refund-reversal', startedAt: minutesAgo(4344), completedAt: daysAgo(3), testStatus: 'PASSED', changedFiles: 5, additions: 122, deletions: 8 }),
];

const APPROVALS = RUNS.filter((r) => r.status === 'WAITING_APPROVAL').map((r, i) => ({ id: 'ap-' + (i + 1), runId: r.id, taskId: r.taskId, projectId: r.projectId, status: 'PENDING', requestedAt: r.completedAt }));

const ENVIRONMENTS = [
  { id: 'env-1', projectId: 'p-ledgerly-api', environment: 'production', appId: 'ledgerly-api-prod', lastDeployStatus: 'SUCCEEDED', lastDeployedAt: minutesAgo(320), commit: 'a91f3c2', triggeredBy: 'Paul Bernard' },
  { id: 'env-2', projectId: 'p-ledgerly-web', environment: 'production', appId: 'ledgerly-web-prod', lastDeployStatus: 'SUCCEEDED', lastDeployedAt: minutesAgo(900), commit: '3bd21aa', triggeredBy: 'Paul Bernard' },
  { id: 'env-3', projectId: 'p-pulse-analytics', environment: 'production', appId: 'pulse-analytics-prod', lastDeployStatus: 'FAILED', lastDeployedAt: minutesAgo(2600), commit: '77e410b', triggeredBy: 'Paul Bernard' },
  { id: 'env-4', projectId: 'p-sendwave', environment: 'production', appId: 'sendwave-prod', lastDeployStatus: 'SUCCEEDED', lastDeployedAt: daysAgo(2), commit: '9c02f11', triggeredBy: 'Paul Bernard' },
];

const INTEGRATIONS = [
  { id: 'int-github', name: 'GitHub', status: 'connected', authType: 'Fine-grained PAT', capabilities: ['Clone', 'Push', 'Pull request', 'Webhook'] },
  { id: 'int-dokploy', name: 'Dokploy', status: 'connected', authType: 'API token', capabilities: ['Deploy', 'Status polling', 'Rollback'] },
  { id: 'int-claude', name: 'Claude Code', status: 'connected', authType: 'Personal subscription login', personalUseOnly: true, capabilities: ['Headless execution', 'Streaming events', 'Permission hooks'] },
  { id: 'int-codex', name: 'OpenAI Codex CLI', status: 'connected', authType: 'API key', capabilities: ['Headless execution', 'JSONL events'] },
  { id: 'int-opencode', name: 'OpenCode', status: 'action-required', authType: 'Not configured', phaseNote: 'Adapter arrives in Phase 12', capabilities: ['Headless execution'] },
  { id: 'int-openhands', name: 'OpenHands', status: 'disconnected', authType: 'Not configured', phaseNote: 'Adapter arrives in Phase 12+', capabilities: ['Sandboxed HTTP/ACP execution'] },
  { id: 'int-shell', name: 'Custom Shell', status: 'connected', authType: 'None (local execution)', capabilities: ['Deterministic scripts'] },
  { id: 'int-telegram', name: 'Telegram', status: 'disconnected', authType: 'Bot token', phaseNote: 'Arrives in Phase 12, Track B', capabilities: ['Task intake', 'Notifications'] },
  { id: 'int-minio', name: 'MinIO', status: 'disconnected', authType: 'S3-compatible credentials', phaseNote: 'Arrives in Phase 16', capabilities: ['Object storage'] },
];

const TEAM = [
  { id: 'u-1', name: 'Paul Bernard', email: 'paul@ledgerly.dev', role: 'OWNER', status: 'active', lastActive: minutesAgo(2), initials: 'PB' },
  { id: 'u-2', name: 'Maya Chen', email: 'maya@contractor.dev', role: 'MAINTAINER', status: 'active', lastActive: minutesAgo(95), initials: 'MC' },
  { id: 'u-3', name: 'Jordan Reyes', email: 'jordan@reyesreview.io', role: 'REVIEWER', status: 'invited', lastActive: null, initials: 'JR' },
  { id: 'u-4', name: 'Accounting Viewer', email: 'books@ledgerly.dev', role: 'VIEWER', status: 'active', lastActive: daysAgo(5), initials: 'AV' },
];

const AUDIT_EVENTS = [
  { id: 'ae-1', actor: 'Paul Bernard', action: 'approval.approved', resource: 'Run r-6', project: 'Ledgerly API', result: 'success', createdAt: daysAgo(3) },
  { id: 'ae-2', actor: 'Talos (system)', action: 'deploy.completed', resource: 'ledgerly-api-prod', project: 'Ledgerly API', result: 'success', createdAt: minutesAgo(320) },
  { id: 'ae-3', actor: 'Talos (system)', action: 'policy.violation.flagged', resource: 'Run r-11', project: 'Pulse Analytics', result: 'flagged', createdAt: minutesAgo(95) },
  { id: 'ae-4', actor: 'Paul Bernard', action: 'auth.login', resource: 'Session', project: null, result: 'success', createdAt: minutesAgo(340) },
  { id: 'ae-5', actor: 'Unknown sender (chat_id 88213)', action: 'telegram.unauthorized_sender', resource: 'Telegram bot', project: null, result: 'blocked', createdAt: daysAgo(1) },
];

const SYSTEM_SERVICES = [
  { id: 'talos-web', name: 'talos-web', kind: 'Angular dashboard', status: 'healthy', latencyMs: 41 },
  { id: 'talos-api', name: 'talos-api', kind: 'Spring Boot API', status: 'healthy', latencyMs: 58, extra: 'Active runs: 2 / 4' },
  { id: 'talos-orchestrator', name: 'talos-orchestrator', kind: 'Python orchestrator', status: 'healthy', latencyMs: 22 },
  { id: 'talos-runner-supervisor', name: 'talos-runner-supervisor', kind: 'Python runner supervisor', status: 'healthy', latencyMs: 34, extra: 'Disk usage: 38%' },
  { id: 'postgres', name: 'PostgreSQL', kind: 'Database (17)', status: 'healthy', latencyMs: 3 },
  { id: 'rabbitmq', name: 'RabbitMQ', kind: 'Queue (4.1)', status: 'healthy', latencyMs: 9, extra: 'Queue depth: 1' },
  { id: 'redis', name: 'Redis', kind: 'Cache / pub-sub (7)', status: 'healthy', latencyMs: 2 },
  { id: 'telegram-adapter', name: 'Telegram adapter', kind: 'Remote trigger (Phase 12)', status: 'unknown', latencyMs: null },
  { id: 'minio', name: 'MinIO', kind: 'Object storage (Phase 16)', status: 'unknown', latencyMs: null },
];

const COST_BY_PROVIDER = [
  { agentKey: 'codex-cli', costUsd: 61.9, tokens: 1284000 },
  { agentKey: 'claude-code', costUsd: null, tokens: 2960000 },
  { agentKey: 'custom-shell', costUsd: 0, tokens: 0 },
];
const COST_MONTHLY_TREND = [
  { month: 'Feb', v: 18.2 }, { month: 'Mar', v: 24.9 }, { month: 'Apr', v: 31.4 },
  { month: 'May', v: 27.8 }, { month: 'Jun', v: 39.6 }, { month: 'Jul', v: 22.1 },
];
const COST_RECOMMENDATIONS = [
  { id: 'rec-1', title: 'Codex CLI is the cheapest capable agent for Pulse Analytics test-fix tasks', detail: 'Last 8 runs: Codex CLI avg $0.71/run vs Claude Code avg $1.38/run at comparable pass rate.' },
  { id: 'rec-2', title: 'Repeated review failures in pulse-analytics/transforms/', detail: '3 of the last 4 runs touching this path were rejected or flagged.' },
];

const MEMORY = {
  'p-ledgerly-api': { chunkCount: 412, lastIndexed: minutesAgo(320), tokenBudget: 6000, conventions: 'Money is always a BigDecimal scaled to 4 places internally, formatted to 2 at the API boundary.', contextDocs: ['docs/architecture.md', 'docs/api-guidelines.md'] },
  'p-pulse-analytics': { chunkCount: 268, lastIndexed: minutesAgo(2600), tokenBudget: 4000, conventions: 'All pipeline stages are pure functions over pandas DataFrames; no in-place mutation.', contextDocs: ['docs/warehouse-schema.md'] },
};
