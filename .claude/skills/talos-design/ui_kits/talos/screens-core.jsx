function statusTone(status) {
  const map = { COMPLETED: 'success', APPROVED: 'success', WAITING_APPROVAL: 'purple', FAILED: 'error', REJECTED: 'error', CANCELLED: 'neutral', QUEUED: 'info', PREPARING_WORKSPACE: 'info', RUNNING_AGENT: 'purple', RUNNING_TESTS: 'purple', REVIEWING: 'purple' };
  return map[status] || 'neutral';
}
const ACTIVE_STATES = ['QUEUED', 'PREPARING_WORKSPACE', 'RUNNING_AGENT', 'RUNNING_TESTS', 'REVIEWING'];
const STEP_LABELS = { WORKSPACE: 'Preparing workspace', AGENT: 'Agent executing', TESTS: 'Running tests', REVIEW: 'Awaiting review' };

function SectionTitle({ title, subtitle }) {
  return (
    <div style={{ marginBottom: 18 }}>
      <div style={{ font: '700 20px var(--font-ui)', letterSpacing: '-0.01em', color: 'var(--text-primary)' }}>{title}</div>
      {subtitle && <div style={{ font: '400 13px var(--font-ui)', color: 'var(--text-muted)', marginTop: 2 }}>{subtitle}</div>}
    </div>
  );
}

function CommandCenterScreen({ data, actions }) {
  const { projects, tasks, runs, approvals, dismissedRecs } = data;
  const activeRuns = runs.filter((r) => ACTIVE_STATES.includes(r.status));
  const pending = approvals.filter((a) => a.status === 'PENDING');
  const failed = runs.filter((r) => r.status === 'FAILED');
  const blocked = tasks.filter((t) => t.status === 'BLOCKED');

  const metrics = [
    { label: 'Active projects', value: projects.length, onClick: () => actions.navigate('projects') },
    { label: 'Running agents', value: activeRuns.filter((r) => r.status === 'RUNNING_AGENT').length, color: 'var(--accent-soft)', onClick: () => actions.navigate('runs') },
    { label: 'Queued runs', value: runs.filter((r) => ['QUEUED', 'PREPARING_WORKSPACE'].includes(r.status)).length, color: 'var(--status-info)', onClick: () => actions.navigate('runs') },
    { label: 'Pending approvals', value: pending.length, color: 'var(--accent-soft)', onClick: () => actions.navigate('reviews') },
    { label: 'Blocked tasks', value: blocked.length, color: 'var(--status-warning)', onClick: () => actions.navigate('board') },
    { label: 'Failed runs', value: failed.length, color: 'var(--status-error)', onClick: () => actions.navigate('runs') },
  ];

  const recs = COST_RECOMMENDATIONS.filter((r) => !dismissedRecs.includes(r.id));

  return (
    <div style={{ maxWidth: 1300 }}>
      <SectionTitle title="Command Center" subtitle="What's running, what needs you, and what to do next." />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6,1fr)', gap: 10, marginBottom: 22 }}>
        {metrics.map((m) => (
          <MetricCard key={m.label} label={m.label} value={m.value} color={m.color} onClick={m.onClick} />
        ))}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1.6fr 1fr', gap: 16, alignItems: 'start' }}>
        <div>
          <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-xl)', padding: 18, marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--accent)', animation: 'talos-pulse-ring 2s infinite' }} />
              <div style={{ font: '700 14px var(--font-ui)', color: 'var(--text-primary)' }}>Live orchestration</div>
              <div style={{ font: '500 12px var(--font-ui)', color: 'var(--text-muted)' }}>{activeRuns.length} active</div>
            </div>
            {activeRuns.length === 0 && <div style={{ padding: '20px 0', textAlign: 'center', color: 'var(--text-muted)' }}>No agents running right now.</div>}
            {activeRuns.map((r) => {
              const task = tasks.find((t) => t.id === r.taskId);
              const project = projects.find((p) => p.id === r.projectId);
              return (
                <button key={r.id} onClick={() => actions.openRun(r.id)} style={{ width: '100%', textAlign: 'left', display: 'block', background: 'var(--surface-sunken)', border: '1px solid var(--border-default)', borderRadius: 11, padding: '13px 14px', marginBottom: 9, cursor: 'pointer' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--accent)', animation: 'talos-pulse-ring 1.8s infinite' }} />
                    <span style={{ font: '600 12.5px var(--font-ui)', color: 'var(--text-primary)' }}>{task ? task.title : r.id}</span>
                  </div>
                  <div style={{ font: '500 11.5px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 8 }}>{project ? project.name : ''} · {AGENT_LABELS[r.agentKey]}</div>
                  <div style={{ font: '600 11px var(--font-ui)', color: 'var(--accent-soft)' }}>{STEP_LABELS[r.currentStep] || r.status}</div>
                </button>
              );
            })}
          </div>
          <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-xl)', padding: 4 }}>
            <div style={{ font: '700 14px var(--font-ui)', padding: '14px 14px 10px', color: 'var(--text-primary)' }}>Project health</div>
            {projects.map((p) => {
              const openTasks = tasks.filter((t) => t.projectId === p.id && t.status !== 'DONE').length;
              return (
                <button key={p.id} onClick={() => actions.openProject(p.id)} style={{ width: '100%', display: 'grid', gridTemplateColumns: '1.6fr 0.8fr 0.6fr 0.8fr', gap: 8, padding: '11px 14px', border: 0, borderTop: '1px solid var(--border-default)', background: 'none', cursor: 'pointer', textAlign: 'left', alignItems: 'center' }}>
                  <div>
                    <div style={{ font: '600 12.5px var(--font-ui)', color: 'var(--text-primary)' }}>{p.name}</div>
                    <div style={{ font: '400 11px var(--font-mono)', color: 'var(--text-muted)' }}>{p.repoUrl}</div>
                  </div>
                  <div style={{ font: '500 11.5px var(--font-ui)', color: 'var(--text-secondary)' }}>{p.stackType}</div>
                  <div style={{ font: '600 12px var(--font-mono)', color: 'var(--text-secondary)' }}>{openTasks}</div>
                  <StatusBadge label={p.testHealth} tone={p.testHealth === 'passing' ? 'success' : p.testHealth === 'failing' ? 'error' : 'neutral'} />
                </button>
              );
            })}
          </div>
        </div>
        <div>
          <div style={{ background: 'var(--surface-card)', border: '1px solid rgba(239,68,68,0.18)', borderRadius: 'var(--r-xl)', padding: 18, marginBottom: 16 }}>
            <div style={{ font: '700 14px var(--font-ui)', marginBottom: 12, color: 'var(--text-primary)' }}>Needs your attention</div>
            {pending.map((a) => {
              const project = projects.find((p) => p.id === a.projectId);
              return (
                <button key={a.id} onClick={() => actions.openReview(a.id)} style={{ width: '100%', textAlign: 'left', display: 'flex', gap: 10, padding: '10px 0', borderTop: '1px solid var(--border-default)', background: 'none', border: 0, cursor: 'pointer' }}>
                  <span style={{ font: '700 9.5px var(--font-ui)', color: 'var(--accent-soft)', background: 'var(--accent-tint-strong)', borderRadius: 5, padding: '2px 6px', height: 'fit-content' }}>REVIEW</span>
                  <span>
                    <div style={{ font: '500 12.5px var(--font-ui)', color: 'var(--text-primary)' }}>Approval requested — {a.runId}</div>
                    <div style={{ font: '400 11.5px var(--font-ui)', color: 'var(--text-muted)' }}>{project ? project.name : ''} · {fmtAgo(a.requestedAt)}</div>
                  </span>
                </button>
              );
            })}
            {failed.map((r) => {
              const project = projects.find((p) => p.id === r.projectId);
              return (
                <button key={r.id} onClick={() => actions.openRun(r.id)} style={{ width: '100%', textAlign: 'left', display: 'flex', gap: 10, padding: '10px 0', borderTop: '1px solid var(--border-default)', background: 'none', border: 0, cursor: 'pointer' }}>
                  <span style={{ font: '700 9.5px var(--font-ui)', color: 'var(--status-error)', background: 'var(--status-error-tint)', borderRadius: 5, padding: '2px 6px', height: 'fit-content' }}>FAILED</span>
                  <span>
                    <div style={{ font: '500 12.5px var(--font-ui)', color: 'var(--text-primary)' }}>Run failed — {r.id}</div>
                    <div style={{ font: '400 11.5px var(--font-ui)', color: 'var(--text-muted)' }}>{project ? project.name : ''}</div>
                  </span>
                </button>
              );
            })}
          </div>
          <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-xl)', padding: 18 }}>
            <div style={{ font: '700 14px var(--font-ui)', marginBottom: 3, color: 'var(--text-primary)' }}>Suggested next actions</div>
            <div style={{ font: '400 11.5px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 12 }}>Advisory only — never auto-executed.</div>
            {recs.map((rec) => (
              <div key={rec.id} style={{ background: 'var(--surface-sunken)', border: '1px solid var(--border-default)', borderRadius: 10, padding: '11px 12px', marginBottom: 9 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                  <div style={{ font: '600 12px var(--font-ui)', color: 'var(--text-primary)', marginBottom: 4 }}>{rec.title}</div>
                  <button onClick={() => actions.dismissRec(rec.id)} style={{ background: 'none', border: 0, color: 'var(--text-muted)', cursor: 'pointer' }}>✕</button>
                </div>
                <div style={{ font: '400 11.5px/1.5 var(--font-ui)', color: 'var(--text-secondary)' }}>{rec.detail}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function ProjectsListScreen({ data, actions }) {
  const { projects, tasks, runs } = data;
  return (
    <div style={{ maxWidth: 1300 }}>
      <SectionTitle title="Projects" subtitle={projects.length + ' registered repositories.'} />
      <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-xl)', overflow: 'hidden' }}>
        {projects.map((p) => {
          const openTasks = tasks.filter((t) => t.projectId === p.id && t.status !== 'DONE').length;
          const activeRuns = runs.filter((r) => r.projectId === p.id && ACTIVE_STATES.includes(r.status)).length;
          return (
            <button key={p.id} onClick={() => actions.openProject(p.id)} style={{ width: '100%', display: 'grid', gridTemplateColumns: '2fr 0.8fr 0.6fr 0.6fr 0.9fr', gap: 10, padding: '14px 16px', border: 0, borderTop: '1px solid var(--border-default)', background: 'none', cursor: 'pointer', textAlign: 'left', alignItems: 'center' }}>
              <div>
                <div style={{ font: '600 13.5px var(--font-ui)', color: 'var(--text-primary)' }}>{p.name}</div>
                <div style={{ font: '400 12px var(--font-ui)', color: 'var(--text-muted)' }}>{p.description}</div>
              </div>
              <div style={{ font: '500 12px var(--font-ui)', color: 'var(--text-secondary)' }}>{p.stackType}</div>
              <div style={{ font: '600 13px var(--font-mono)', color: 'var(--text-secondary)' }}>{openTasks}</div>
              <div style={{ font: '600 13px var(--font-mono)', color: 'var(--accent-soft)' }}>{activeRuns}</div>
              <StatusBadge label={p.integrationHealth} tone={p.integrationHealth === 'healthy' ? 'success' : p.integrationHealth === 'action-required' ? 'error' : 'warning'} />
            </button>
          );
        })}
      </div>
    </div>
  );
}

function ProjectDetailScreen({ data, actions }) {
  const { project, tasks, runs, approvals } = data;
  const [tab, setTab] = React.useState('overview');
  if (!project) return null;
  const projRuns = runs.filter((r) => r.projectId === project.id);
  const projTasks = tasks.filter((t) => t.projectId === project.id);
  const projReviews = approvals.filter((a) => a.projectId === project.id);
  return (
    <div style={{ maxWidth: 1200 }}>
      <button onClick={() => actions.navigate('projects')} style={{ background: 'none', border: 0, color: 'var(--text-muted)', cursor: 'pointer', marginBottom: 10, font: '500 12px var(--font-ui)' }}>← Projects</button>
      <div style={{ font: '700 21px var(--font-ui)', color: 'var(--text-primary)', marginBottom: 4 }}>{project.name}</div>
      <div style={{ font: '400 13px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 6 }}>{project.description}</div>
      <div style={{ font: '400 11.5px var(--font-mono)', color: 'var(--text-muted)', marginBottom: 18 }}>{project.repoUrl} @ {project.defaultBranch} · {project.stackType}</div>
      <Tabs items={[{ key: 'overview', label: 'Overview' }, { key: 'runs', label: 'Runs' }, { key: 'reviews', label: 'Reviews' }, { key: 'configuration', label: 'Configuration' }]} activeKey={tab} onChange={setTab} />
      <div style={{ marginTop: 18 }}>
        {tab === 'overview' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {projTasks.map((t) => (
              <button key={t.id} onClick={() => actions.openTask(t.id)} style={{ textAlign: 'left', background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 10, padding: '10px 12px', cursor: 'pointer' }}>
                <span style={{ font: '600 12.5px var(--font-ui)', color: 'var(--text-primary)' }}>{t.title}</span>
                <span style={{ float: 'right' }}><StatusBadge label={t.status} tone={statusTone(t.status)} /></span>
              </button>
            ))}
          </div>
        )}
        {tab === 'runs' && (
          <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', overflow: 'hidden' }}>
            {projRuns.map((r) => (
              <RunRow
                key={r.id}
                run={{ id: r.id, taskTitle: (tasks.find((t) => t.id === r.taskId) || {}).title || '', projectName: project.name, agentKey: r.agentKey, authMode: r.providerAuthMode, duration: '—', cost: r.costUsd !== undefined ? fmtMoney(r.costUsd) : 'subscription', status: r.status, statusTone: statusTone(r.status) }}
                onClick={() => actions.openRun(r.id)}
              />
            ))}
          </div>
        )}
        {tab === 'reviews' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {projReviews.length === 0 && <div style={{ color: 'var(--text-muted)' }}>No approval requests for this project.</div>}
            {projReviews.map((a) => (
              <button key={a.id} onClick={() => actions.openReview(a.id)} style={{ textAlign: 'left', background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 10, padding: '10px 12px', cursor: 'pointer', color: 'var(--text-primary)' }}>{a.id} — {a.status}</button>
            ))}
          </div>
        )}
        {tab === 'configuration' && (
          <pre style={{ background: 'var(--surface-sunken)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 18, font: '12.5px/1.7 var(--font-mono)', color: 'var(--text-secondary)', whiteSpace: 'pre-wrap' }}>
{`project:
  name: ${project.name}
  type: ${project.stackType}
  repo: ${project.repoUrl}
commands:
  test: "${project.testCommand}"
agents:
  preferred: ${project.agentDefault}
rules:
  require_approval_for: [production_deploy, database_migration]
deploy:
  provider: dokploy
  approval_required: true`}
          </pre>
        )}
      </div>
    </div>
  );
}

function TaskBoardScreen({ data, actions }) {
  const { tasks, projects, runs } = data;
  const [dragId, setDragId] = React.useState(null);
  const [overCol, setOverCol] = React.useState(null);
  const columns = ['BACKLOG', 'READY', 'RUNNING', 'REVIEW', 'BLOCKED', 'DONE'];
  const VALID = { BACKLOG: ['READY'], READY: ['BACKLOG', 'RUNNING'], BLOCKED: ['READY', 'BACKLOG'], RUNNING: [], REVIEW: [], DONE: [] };

  function drop(col) {
    setOverCol(null);
    const task = tasks.find((t) => t.id === dragId);
    setDragId(null);
    if (!task || task.status === col) return;
    if (!(VALID[task.status] || []).includes(col)) {
      actions.toast('Can’t move a task from ' + task.status + ' to ' + col + ' directly.', 'error');
      return;
    }
    if (col === 'RUNNING') { actions.requestStartRun(task.id); return; }
    actions.moveTask(task.id, col);
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 16 }}>
        <SectionTitle title="Task Board" subtitle="Drag cards between columns. Cancelled tasks are filterable, not a column." />
        <Button variant="primary" onClick={actions.newTask}>+ New task</Button>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6,minmax(190px,1fr))', gap: 12, overflowX: 'auto', paddingBottom: 20 }}>
        {columns.map((col) => {
          const colTasks = tasks.filter((t) => t.status === col);
          return (
            <div
              key={col}
              onDragOver={(e) => { e.preventDefault(); setOverCol(col); }}
              onDragLeave={() => setOverCol(null)}
              onDrop={() => drop(col)}
              style={{ background: overCol === col ? 'var(--surface-hover)' : 'var(--surface-sunken)', borderRadius: 'var(--r-lg)', padding: 10, minHeight: 200 }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 6px 10px' }}>
                <span style={{ font: '700 11.5px var(--font-ui)', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '.03em' }}>{col}</span>
                <span style={{ font: '600 11px var(--font-mono)', color: 'var(--text-muted)', background: 'rgba(255,255,255,0.06)', borderRadius: 6, padding: '1px 7px' }}>{colTasks.length}</span>
              </div>
              {colTasks.map((t) => {
                const project = projects.find((p) => p.id === t.projectId);
                const activeRun = runs.find((r) => r.taskId === t.id && ACTIVE_STATES.includes(r.status));
                return (
                  <div key={t.id} style={{ marginBottom: 9 }}>
                    <TaskCard
                      task={{ title: t.title, projectName: project ? project.name : '', priority: t.priority, agentKey: t.agentKey, riskLevel: t.riskLevel, blockedReason: t.blockedReason, activeStepLabel: activeRun ? (STEP_LABELS[activeRun.currentStep] || activeRun.status) : null }}
                      onClick={() => actions.openTask(t.id)}
                      onDragStart={() => setDragId(t.id)}
                    />
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}
