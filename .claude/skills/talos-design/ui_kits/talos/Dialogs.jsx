function TaskDrawer({ task, project, runs, onClose, onStartRun, onOpenRun }) {
  if (!task) return null;
  const activeRun = runs.find((r) => r.taskId === task.id && ACTIVE_STATES.includes(r.status));
  return (
    <div onClick={onClose} style={{ position: 'fixed', inset: 0, background: 'var(--scrim)', zIndex: 105, display: 'flex', justifyContent: 'flex-end' }}>
      <div onClick={(e) => e.stopPropagation()} style={{ width: 440, maxWidth: '92vw', height: '100%', background: 'var(--surface-card)', borderLeft: '1px solid var(--border-strong)', boxShadow: 'var(--shadow-4)', overflowY: 'auto', padding: 22 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
          <div style={{ font: '700 17px var(--font-ui)', color: 'var(--text-primary)', flex: 1, paddingRight: 10 }}>{task.title}</div>
          <button onClick={onClose} style={{ background: 'none', border: 0, color: 'var(--text-muted)', cursor: 'pointer' }}>✕</button>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 16, flexWrap: 'wrap' }}>
          <StatusBadge label={task.status} tone={statusTone(task.status)} />
          <AgentBadge agentKey={task.agentKey} />
          {task.riskLevel === 'HIGH' && <RiskBadge level="HIGH" />}
          <span style={{ font: '600 10.5px var(--font-ui)', color: 'var(--text-muted)' }}>{project ? project.name : ''}</span>
        </div>
        <div style={{ font: '400 13px/1.6 var(--font-ui)', color: 'var(--text-secondary)', marginBottom: 18 }}>{task.description || 'No description provided.'}</div>
        {task.blockedReason && <div style={{ background: 'var(--status-warning-tint)', border: '1px solid rgba(245,158,11,0.25)', borderRadius: 9, padding: '10px 12px', marginBottom: 16, font: '400 12.5px var(--font-ui)', color: '#F0B849' }}>Blocked: {task.blockedReason}</div>}
        <div style={{ display: 'flex', gap: 8, marginBottom: 22 }}>
          {!activeRun && (task.status === 'READY' || task.status === 'BACKLOG' || task.status === 'BLOCKED') && <Button variant="primary" onClick={() => onStartRun(task.id)}>Start run</Button>}
          {activeRun && <Button variant="secondary" onClick={() => onOpenRun(activeRun.id)}>View active run</Button>}
        </div>
      </div>
    </div>
  );
}

function NewTaskDialog({ open, onClose, projects, onCreate }) {
  const [projectId, setProjectId] = React.useState(projects[0] ? projects[0].id : '');
  const [title, setTitle] = React.useState('');
  const [description, setDescription] = React.useState('');
  React.useEffect(() => { if (open) { setTitle(''); setDescription(''); } }, [open]);
  return (
    <Dialog open={open} onClose={onClose} title="New task">
      <form onSubmit={(e) => { e.preventDefault(); if (!title.trim()) return; onCreate({ projectId, title, description }); }}>
        <div style={{ marginBottom: 14 }}>
          <label style={{ display: 'block', font: '600 11.5px var(--font-ui)', color: 'var(--text-secondary)', marginBottom: 6 }}>Project</label>
          <Select value={projectId} onChange={(e) => setProjectId(e.target.value)} options={projects.map((p) => ({ value: p.id, label: p.name }))} />
        </div>
        <div style={{ marginBottom: 14 }}>
          <label style={{ display: 'block', font: '600 11.5px var(--font-ui)', color: 'var(--text-secondary)', marginBottom: 6 }}>Title</label>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Add pagination to GET /customers" />
        </div>
        <div style={{ marginBottom: 18 }}>
          <label style={{ display: 'block', font: '600 11.5px var(--font-ui)', color: 'var(--text-secondary)', marginBottom: 6 }}>Description</label>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} placeholder="What should the agent do?" style={{ width: '100%', boxSizing: 'border-box', background: 'var(--surface-sunken)', border: '1px solid var(--border-strong)', borderRadius: 9, padding: '9px 10px', color: 'var(--text-primary)', font: '400 13px var(--font-ui)' }} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button variant="primary">Create task</Button>
        </div>
      </form>
    </Dialog>
  );
}

function StartRunDialog({ task, project, onClose, onConfirm }) {
  const [agentKey, setAgentKey] = React.useState('claude-code');
  const [authMode, setAuthMode] = React.useState('subscription_local');
  React.useEffect(() => { if (task) { setAgentKey((project && project.agentDefault) || 'claude-code'); setAuthMode('subscription_local'); } }, [task]);
  if (!task) return null;
  const slug = task.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 24);
  return (
    <Dialog open={!!task} onClose={onClose} title="Start agent run?" consequence="Review the run parameters before Talos creates an isolated workspace.">
      <div style={{ background: 'var(--surface-sunken)', border: '1px solid var(--border-default)', borderRadius: 10, padding: '12px 14px', marginBottom: 14, font: '400 12.5px var(--font-ui)' }}>
        <Row label="Task" value={task.title} />
        <Row label="Project" value={project ? project.name : ''} />
        <Row label="Branch" value={'agent/task-' + task.id + '-' + slug} mono />
        <Row label="Timeout" value="30 min" />
      </div>
      <div style={{ marginBottom: 12 }}>
        <div style={{ font: '600 11.5px var(--font-ui)', color: 'var(--text-secondary)', marginBottom: 6 }}>Agent</div>
        <div style={{ display: 'flex', gap: 6 }}>
          {['claude-code', 'codex-cli', 'custom-shell'].map((a) => (
            <button key={a} onClick={() => setAgentKey(a)} style={{ flex: 1, padding: 8, borderRadius: 8, border: '1px solid ' + (agentKey === a ? 'var(--accent)' : 'var(--border-strong)'), background: agentKey === a ? 'var(--accent)' : 'transparent', color: '#fff', font: '600 11px var(--font-ui)', cursor: 'pointer' }}>{AGENT_LABELS[a]}</button>
          ))}
        </div>
      </div>
      <div style={{ marginBottom: 20 }}>
        <div style={{ font: '600 11.5px var(--font-ui)', color: 'var(--text-secondary)', marginBottom: 6 }}>Authentication mode</div>
        <div style={{ display: 'flex', gap: 6 }}>
          {[['subscription_local', 'Personal subscription'], ['api_key', 'API key']].map(([v, l]) => (
            <button key={v} onClick={() => setAuthMode(v)} style={{ flex: 1, padding: 8, borderRadius: 8, border: '1px solid ' + (authMode === v ? 'var(--accent)' : 'var(--border-strong)'), background: authMode === v ? 'var(--accent)' : 'transparent', color: '#fff', font: '600 11px var(--font-ui)', cursor: 'pointer' }}>{l}</button>
          ))}
        </div>
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button variant="primary" onClick={() => onConfirm(task.id, agentKey, authMode)}>Start run</Button>
      </div>
    </Dialog>
  );
}

function Row({ label, value, mono }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
      <span style={{ color: 'var(--text-muted)' }}>{label}</span>
      <span style={{ color: 'var(--text-primary)', fontFamily: mono ? 'var(--font-mono)' : undefined, fontWeight: mono ? 400 : 600, fontSize: mono ? 11 : undefined }}>{value}</span>
    </div>
  );
}

function ReviewDecisionDialog({ pending, onClose, onSubmit }) {
  const [notes, setNotes] = React.useState('');
  React.useEffect(() => { setNotes(''); }, [pending]);
  if (!pending) return null;
  const titleMap = { approve: 'Approve this run?', reject: 'Reject this run?', changes: 'Request changes?' };
  const consequenceMap = {
    approve: 'Talos will commit, push the branch, and open a pull request. This cannot be undone.',
    reject: 'The task returns to Ready. The agent-authored branch is kept for reference.',
    changes: 'The task returns to Ready with your notes so the next run can address them.',
  };
  const notesRequired = pending.type !== 'approve';
  return (
    <Dialog open={!!pending} onClose={onClose} title={titleMap[pending.type]} consequence={consequenceMap[pending.type]}>
      {notesRequired && (
        <div style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', font: '600 11.5px var(--font-ui)', color: 'var(--text-secondary)', marginBottom: 6 }}>Notes (required)</label>
          <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} placeholder="Explain what needs to change or why this is rejected…" style={{ width: '100%', boxSizing: 'border-box', background: 'var(--surface-sunken)', border: '1px solid var(--border-strong)', borderRadius: 9, padding: '9px 10px', color: 'var(--text-primary)', font: '400 13px var(--font-ui)' }} />
        </div>
      )}
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
        <Button variant="secondary" onClick={onClose}>Back</Button>
        <Button variant={pending.type === 'approve' ? 'primary' : pending.type === 'reject' ? 'danger' : 'secondary'} onClick={() => { if (notesRequired && !notes.trim()) return; onSubmit(pending.id, pending.type, notes); }}>
          {pending.type === 'approve' ? 'Approve & push' : pending.type === 'reject' ? 'Reject run' : 'Request changes'}
        </Button>
      </div>
    </Dialog>
  );
}

function CommandPalette({ open, onClose, query, onQuery, groups }) {
  if (!open) return null;
  return (
    <div onClick={onClose} style={{ position: 'fixed', inset: 0, background: 'var(--scrim)', zIndex: 150, display: 'flex', alignItems: 'flex-start', justifyContent: 'center', paddingTop: '14vh' }}>
      <div onClick={(e) => e.stopPropagation()} style={{ width: 600, maxWidth: '92vw', background: 'var(--surface-elevated)', border: '1px solid var(--border-strong)', borderRadius: 'var(--r-xl)', boxShadow: 'var(--shadow-4)', overflow: 'hidden' }}>
        <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--border-default)' }}>
          <input autoFocus value={query} onChange={(e) => onQuery(e.target.value)} placeholder="Navigate, search, or run a command…" style={{ width: '100%', background: 'none', border: 0, outline: 'none', color: 'var(--text-primary)', font: '400 14px var(--font-ui)' }} />
        </div>
        <div style={{ maxHeight: 380, overflowY: 'auto', padding: 8 }}>
          {groups.map((g) => (
            <div key={g.label}>
              <div style={{ font: '700 10.5px var(--font-ui)', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '.04em', padding: '8px 10px 4px' }}>{g.label}</div>
              {g.items.map((it, i) => (
                <button key={i} onClick={it.onClick} style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 10, textAlign: 'left', padding: '9px 10px', borderRadius: 8, border: 0, background: 'none', color: 'var(--text-primary)', cursor: 'pointer' }}>
                  <span style={{ font: '500 13px var(--font-ui)' }}>{it.title}</span>
                </button>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
