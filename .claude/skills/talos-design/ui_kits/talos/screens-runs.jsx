function RunsListScreen({ data, actions }) {
  const { runs, tasks, projects } = data;
  return (
    <div style={{ maxWidth: 1300 }}>
      <SectionTitle title="Agent Runs" subtitle="Every execution across every project, from queued to completed." />
      <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-xl)', overflow: 'hidden' }}>
        {runs.map((r) => {
          const task = tasks.find((t) => t.id === r.taskId);
          const project = projects.find((p) => p.id === r.projectId);
          return (
            <RunRow
              key={r.id}
              run={{ id: r.id, taskTitle: task ? task.title : '', projectName: project ? project.name : '', agentKey: r.agentKey, authMode: r.providerAuthMode, duration: r.completedAt ? '—' : 'active', cost: r.costUsd !== undefined ? fmtMoney(r.costUsd) : 'subscription', status: r.status, statusTone: statusTone(r.status) }}
              onClick={() => actions.openRun(r.id)}
            />
          );
        })}
      </div>
    </div>
  );
}

function RunDetailScreen({ data, actions }) {
  const { run, task, project } = data;
  const [tab, setTab] = React.useState('logs');
  if (!run) return null;
  const stepDefs = ['WORKSPACE', 'AGENT', 'TESTS', 'REVIEW', 'PUSH', 'PR', 'DEPLOY'];
  const idx = { CREATED: 0, QUEUED: 0, PREPARING_WORKSPACE: 0, RUNNING_AGENT: 1, RUNNING_TESTS: 2, WAITING_APPROVAL: 3, APPROVED: 4, COMPLETED: 6, FAILED: -1, REJECTED: -1, CANCELLED: -1 }[run.status];
  const steps = stepDefs.map((s, i) => ({ key: s, label: { WORKSPACE: 'Workspace prep', AGENT: 'Agent execution', TESTS: 'Test execution', REVIEW: 'Policy scan + review', PUSH: 'Commit & push', PR: 'Pull request', DEPLOY: 'Deploy trigger' }[s], state: run.status === 'FAILED' ? (i < Math.max(0, idx + 1) ? 'COMPLETED' : i === Math.max(0, idx + 1) ? 'FAILED' : 'SKIPPED') : i < idx || run.status === 'COMPLETED' ? 'COMPLETED' : i === idx ? 'RUNNING' : 'PENDING' }));
  const diff = run.changedFiles
    ? [{ path: project && project.stackType === 'spring-boot' ? 'src/main/java/dev/talos/billing/TaxCalculator.java' : 'src/index.js', additions: run.additions, deletions: run.deletions, risk: !!run.matchedPattern, riskLabel: run.matchedPattern ? 'Matches policy pattern ' + run.matchedPattern : null, hunkText: '@@ -1,' + run.deletions + ' +1,' + run.additions + ' @@\n- // previous implementation\n+ // updated implementation per task description' }]
    : [];
  const tabs = ['logs', 'steps', 'changes', 'tests', 'artifacts', 'audit'];

  return (
    <div style={{ maxWidth: 1300 }}>
      <button onClick={() => actions.navigate('runs')} style={{ background: 'none', border: 0, color: 'var(--text-muted)', cursor: 'pointer', marginBottom: 10, font: '500 12px var(--font-ui)' }}>← Agent Runs</button>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 16 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <span style={{ font: '700 20px var(--font-mono)', color: 'var(--text-primary)' }}>{run.id}</span>
            <StatusBadge label={run.status} tone={statusTone(run.status)} />
          </div>
          <div style={{ font: '500 13.5px var(--font-ui)', color: 'var(--text-secondary)' }}>{task ? task.title : ''} · {project ? project.name : ''}</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {ACTIVE_STATES.includes(run.status) && <Button variant="danger" onClick={() => actions.cancelRun(run.id)}>Cancel</Button>}
          {run.status === 'FAILED' && <Button variant="secondary" onClick={() => actions.retryRun(run.id)}>Retry</Button>}
          {run.status === 'WAITING_APPROVAL' && <Button variant="primary" onClick={() => actions.openReviewForRun(run.id)}>Review →</Button>}
        </div>
      </div>
      {run.errorMessage && <div style={{ background: 'var(--status-error-tint)', border: '1px solid rgba(239,68,68,0.25)', borderRadius: 10, padding: '11px 14px', marginBottom: 14, font: '500 12.5px var(--font-ui)', color: '#f5a3a3' }}>{run.errorMessage}</div>}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10, marginBottom: 18 }}>
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 10, padding: 11 }}><div style={{ font: '600 10px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 5 }}>AGENT</div><AgentBadge agentKey={run.agentKey} /></div>
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 10, padding: 11 }}><div style={{ font: '600 10px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 5 }}>AUTH MODE</div><AuthModeBadge mode={run.providerAuthMode} /></div>
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 10, padding: 11 }}><div style={{ font: '600 10px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 5 }}>TIMEOUT</div><div style={{ font: '600 13px var(--font-mono)', color: 'var(--text-primary)' }}>30 min</div></div>
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 10, padding: 11 }}><div style={{ font: '600 10px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 5 }}>COST</div><div style={{ font: '600 13px var(--font-mono)', color: 'var(--text-primary)' }}>{run.costUsd !== undefined ? fmtMoney(run.costUsd) : 'Not reported'}</div></div>
      </div>
      <div style={{ marginBottom: 18 }}><StatusTimeline steps={steps} /></div>
      <Tabs items={tabs.map((t) => ({ key: t, label: t.charAt(0).toUpperCase() + t.slice(1) }))} activeKey={tab} onChange={setTab} />
      <div style={{ marginTop: 16 }}>
        {tab === 'logs' && (
          <div style={{ background: 'var(--surface-sunken)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: '12px 14px', font: '12px/1.7 var(--font-mono)', color: 'var(--text-secondary)', minHeight: 160 }}>
            <div>[system] Assembling prompt (constraints + project context + task)…</div>
            <div>[system] Starting agent process in isolated workspace…</div>
            <div>[system] Reading relevant source files…</div>
            {run.status === 'COMPLETED' || run.status === 'WAITING_APPROVAL' ? <div>[system] BUILD SUCCESS — all tests passed.</div> : null}
          </div>
        )}
        {tab === 'steps' && (
          <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)' }}>
            {steps.map((s) => (
              <div key={s.key} style={{ display: 'flex', justifyContent: 'space-between', padding: '12px 16px', borderTop: '1px solid var(--border-default)' }}>
                <span style={{ font: '600 13px var(--font-ui)', color: 'var(--text-primary)' }}>{s.label}</span>
                <span style={{ font: '600 11px var(--font-ui)', color: s.state === 'FAILED' ? 'var(--status-error)' : s.state === 'COMPLETED' ? 'var(--status-success)' : s.state === 'RUNNING' ? 'var(--accent-soft)' : 'var(--text-muted)' }}>{s.state}</span>
              </div>
            ))}
          </div>
        )}
        {tab === 'changes' && (diff.length ? diff.map((f, i) => <DiffBlock key={i} file={f} />) : <div style={{ color: 'var(--text-muted)' }}>No diff captured for this run.</div>)}
        {tab === 'tests' && (
          <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 16 }}>
            <div style={{ font: '600 12px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 6 }}>COMMAND</div>
            <div style={{ font: '500 12px var(--font-mono)', color: 'var(--text-primary)', marginBottom: 14 }}>{project ? project.testCommand : ''}</div>
            <div style={{ font: '600 13px var(--font-ui)', color: run.testStatus === 'PASSED' ? 'var(--status-success)' : run.testStatus === 'FAILED' ? 'var(--status-error)' : 'var(--text-muted)' }}>{run.testStatus}</div>
          </div>
        )}
        {tab === 'artifacts' && (
          <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)' }}>
            {['transcript.txt', 'diff.patch', 'test-report.xml'].map((n) => (
              <div key={n} style={{ display: 'flex', gap: 14, padding: '12px 16px', borderTop: '1px solid var(--border-default)', alignItems: 'center' }}>
                <span style={{ font: '600 12.5px var(--font-mono)', color: 'var(--text-primary)', flex: 1 }}>{n}</span>
                <Button variant="secondary" size="sm">Download</Button>
              </div>
            ))}
          </div>
        )}
        {tab === 'audit' && <div style={{ color: 'var(--text-muted)' }}>Run r-mutations and actor trail appear here.</div>}
      </div>
    </div>
  );
}

function ReviewsListScreen({ data, actions }) {
  const { approvals, runs, tasks, projects } = data;
  const pending = approvals.filter((a) => a.status === 'PENDING');
  return (
    <div style={{ maxWidth: 1200 }}>
      <SectionTitle title="Review Center" subtitle="Nothing pushes, opens a PR, or deploys without your approval here." />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {pending.length === 0 && <div style={{ color: 'var(--text-muted)' }}>Nothing waiting for approval right now.</div>}
        {pending.map((a) => {
          const run = runs.find((r) => r.id === a.runId);
          const task = tasks.find((t) => t.id === a.taskId);
          const project = projects.find((p) => p.id === a.projectId);
          return (
            <button key={a.id} onClick={() => actions.openReview(a.id)} style={{ textAlign: 'left', background: 'var(--surface-card)', border: '1px solid var(--accent-border)', borderRadius: 12, padding: '15px 16px', cursor: 'pointer' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                <span style={{ font: '600 13.5px var(--font-ui)', color: 'var(--text-primary)' }}>{task ? task.title : ''}</span>
                <span style={{ font: '500 11.5px var(--font-ui)', color: 'var(--text-muted)' }}>{fmtAgo(a.requestedAt)}</span>
              </div>
              <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                <span style={{ font: '500 11.5px var(--font-ui)', color: 'var(--text-muted)' }}>{project ? project.name : ''}</span>
                <AgentBadge agentKey={run ? run.agentKey : 'claude-code'} />
                {run && run.reviewStatus === 'RISK_FLAGGED' && <RiskBadge level="HIGH" />}
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}

function ReviewDetailScreen({ data, actions }) {
  const { approval, run, task, project } = data;
  if (!approval) return null;
  const diff = run.changedFiles ? [{ path: project && project.stackType === 'python' ? 'db/migrations/V014__cohort_retention_view.sql' : 'src/main/java/dev/talos/billing/TaxCalculator.java', additions: run.additions, deletions: run.deletions, risk: !!run.matchedPattern, riskLabel: run.matchedPattern ? 'Matches policy pattern ' + run.matchedPattern : null, hunkText: '@@ -1,' + run.deletions + ' +1,' + run.additions + ' @@\n+ // agent-authored change' }] : [];
  const decided = approval.status !== 'PENDING';
  return (
    <div style={{ maxWidth: 1300, display: 'grid', gridTemplateColumns: '1.6fr 1fr', gap: 18 }}>
      <div>
        <button onClick={() => actions.navigate('reviews')} style={{ background: 'none', border: 0, color: 'var(--text-muted)', cursor: 'pointer', marginBottom: 10, font: '500 12px var(--font-ui)' }}>← Review Center</button>
        <div style={{ font: '700 19px var(--font-ui)', color: 'var(--text-primary)', marginBottom: 4 }}>{task ? task.title : ''}</div>
        <div style={{ font: '500 12.5px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 16 }}>{project ? project.name : ''} · requested {fmtAgo(approval.requestedAt)}</div>
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 15, marginBottom: 14 }}>
          <div style={{ font: '700 12.5px var(--font-ui)', marginBottom: 8, color: 'var(--text-primary)' }}>Agent summary</div>
          <div style={{ font: '400 12.5px/1.6 var(--font-ui)', color: 'var(--text-secondary)' }}>{run.summary}</div>
        </div>
        {diff.map((f, i) => <DiffBlock key={i} file={f} />)}
      </div>
      <div>
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 15, marginBottom: 14 }}>
          <div style={{ font: '700 12.5px var(--font-ui)', marginBottom: 10, color: 'var(--text-primary)' }}>Checks</div>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', font: '500 12px var(--font-ui)' }}><span style={{ color: 'var(--text-muted)' }}>Tests</span><span style={{ color: 'var(--status-success)', fontWeight: 700 }}>{run.testStatus}</span></div>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', font: '500 12px var(--font-ui)' }}><span style={{ color: 'var(--text-muted)' }}>Secret exposure</span><span style={{ color: 'var(--status-success)', fontWeight: 700 }}>None detected</span></div>
        </div>
        {!decided ? (
          <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 15, display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Button variant="primary" onClick={() => actions.decide(approval.id, 'approve')}>Approve &amp; push</Button>
            <Button variant="secondary" onClick={() => actions.decide(approval.id, 'changes')}>Request changes</Button>
            <Button variant="danger" onClick={() => actions.decide(approval.id, 'reject')}>Reject</Button>
          </div>
        ) : (
          <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 15, textAlign: 'center', color: 'var(--text-muted)' }}>Decision: {approval.status}</div>
        )}
      </div>
    </div>
  );
}
