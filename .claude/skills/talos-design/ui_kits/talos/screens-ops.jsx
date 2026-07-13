function DeploymentsScreen({ data, actions }) {
  const { environments, projects, approvals } = data;
  return (
    <div style={{ maxWidth: 1200 }}>
      <SectionTitle title="Deployments" subtitle="Every deploy target across every project, gated on approval." />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 14 }}>
        {environments.map((e) => {
          const project = projects.find((p) => p.id === e.projectId);
          const hasApproved = approvals.some((a) => a.projectId === e.projectId && a.status === 'APPROVED');
          const canDeploy = e.lastDeployStatus === 'SUCCEEDED' || hasApproved;
          return (
            <div key={e.id} style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <div>
                  <div style={{ font: '700 14px var(--font-ui)', color: 'var(--text-primary)' }}>{project ? project.name : ''}</div>
                  <div style={{ font: '600 11px var(--font-ui)', color: 'var(--text-muted)', textTransform: 'capitalize' }}>{e.environment}</div>
                </div>
                <StatusBadge label={e.lastDeployStatus} tone={e.lastDeployStatus === 'SUCCEEDED' ? 'success' : e.lastDeployStatus === 'FAILED' ? 'error' : 'info'} />
              </div>
              <div style={{ font: '400 11.5px var(--font-mono)', color: 'var(--text-muted)', marginBottom: 2 }}>{e.appId} · {e.commit}</div>
              <div style={{ font: '400 11.5px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 14 }}>Last deployed {fmtAgo(e.lastDeployedAt)} by {e.triggeredBy}</div>
              <div style={{ display: 'flex', gap: 8 }}>
                <Button variant="primary" disabled={!canDeploy} onClick={() => actions.deploy(e.id)}>Deploy</Button>
                <Button variant="secondary" onClick={() => actions.rollback(e.id)}>Rollback</Button>
              </div>
              {!canDeploy && <div style={{ marginTop: 8, font: '500 11.5px var(--font-ui)', color: 'var(--status-warning)' }}>Requires an approved run before deploying.</div>}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function IntegrationsScreen({ data }) {
  const { integrations } = data;
  return (
    <div style={{ maxWidth: 1200 }}>
      <SectionTitle title="Integrations" subtitle="Providers, source control, and delivery channels behind the adapter interface." />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 12 }}>
        {integrations.map((i) => (
          <div key={i.id} style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 15 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <div style={{ font: '700 13.5px var(--font-ui)', color: 'var(--text-primary)' }}>{i.name}</div>
              <StatusBadge label={i.status} tone={i.status === 'connected' ? 'success' : i.status === 'action-required' ? 'warning' : 'neutral'} />
            </div>
            <div style={{ font: '500 11.5px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 4 }}>{i.authType}</div>
            {i.personalUseOnly && <div style={{ font: '600 10.5px var(--font-ui)', color: 'var(--status-warning)' }}>Personal use only</div>}
            {i.phaseNote && <div style={{ font: '500 11px var(--font-ui)', color: 'var(--text-muted)', marginTop: 4 }}>{i.phaseNote}</div>}
          </div>
        ))}
      </div>
    </div>
  );
}

function MemoryScreen({ data, actions }) {
  const { projects, memoryProjectId } = data;
  const activeId = memoryProjectId || projects[0].id;
  const project = projects.find((p) => p.id === activeId);
  const mem = MEMORY[activeId];
  return (
    <div style={{ maxWidth: 1200 }}>
      <SectionTitle title="Memory & Docs" subtitle="Project conventions, decisions, and ingested docs — always isolated per project." />
      <select value={activeId} onChange={(e) => actions.setMemoryProject(e.target.value)} style={{ background: 'var(--surface-card)', border: '1px solid var(--border-strong)', borderRadius: 8, padding: '8px 11px', color: 'var(--text-primary)', font: '500 12.5px var(--font-ui)', marginBottom: 18 }}>
        {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
      </select>
      {mem ? (
        <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: 16 }}>
          <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 16 }}>
            <div style={{ font: '700 13px var(--font-ui)', marginBottom: 8, color: 'var(--text-primary)' }}>Project conventions</div>
            <div style={{ font: '400 12.5px/1.6 var(--font-ui)', color: 'var(--text-secondary)' }}>{mem.conventions}</div>
          </div>
          <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 16 }}>
            <div style={{ font: '700 13px var(--font-ui)', marginBottom: 10, color: 'var(--text-primary)' }}>Ingestion status</div>
            <div style={{ font: '600 12px var(--font-ui)', marginBottom: 6, color: 'var(--text-primary)' }}>{mem.chunkCount} chunks indexed</div>
            <div style={{ font: '400 11.5px var(--font-ui)', color: 'var(--text-muted)' }}>Last indexed {fmtAgo(mem.lastIndexed)}</div>
            <div style={{ marginTop: 12, padding: 10, borderRadius: 8, background: 'rgba(56,189,248,0.08)', border: '1px solid rgba(56,189,248,0.2)', font: '400 12px/1.5 var(--font-ui)', color: '#7dd3f7' }}>Memory is project-isolated by construction.</div>
          </div>
        </div>
      ) : (
        <div style={{ color: 'var(--text-muted)' }}>No memory ingested for this project yet.</div>
      )}
    </div>
  );
}

function CostsScreen({ data }) {
  const max = Math.max(...COST_MONTHLY_TREND.map((m) => m.v));
  return (
    <div style={{ maxWidth: 1200 }}>
      <SectionTitle title="Costs & Insights" subtitle="Provider spend, usage, and quality signals across every project." />
      <div style={{ display: 'grid', gridTemplateColumns: '1.3fr 1fr', gap: 16 }}>
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 16 }}>
          <div style={{ font: '700 13px var(--font-ui)', marginBottom: 14, color: 'var(--text-primary)' }}>Monthly trend (API-key providers)</div>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 10, height: 130 }}>
            {COST_MONTHLY_TREND.map((m) => (
              <div key={m.month} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, height: '100%', justifyContent: 'flex-end' }}>
                <span style={{ font: '600 10.5px var(--font-mono)', color: 'var(--text-muted)' }}>${m.v.toFixed(0)}</span>
                <div style={{ width: '100%', height: (m.v / max) * 100 + '%', background: 'linear-gradient(180deg,var(--accent-soft),var(--accent))', borderRadius: '5px 5px 0 0' }} />
                <span style={{ font: '600 10.5px var(--font-ui)', color: 'var(--text-muted)' }}>{m.month}</span>
              </div>
            ))}
          </div>
        </div>
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 16 }}>
          <div style={{ font: '700 13px var(--font-ui)', marginBottom: 14, color: 'var(--text-primary)' }}>Cost by provider</div>
          {COST_BY_PROVIDER.map((c) => (
            <div key={c.agentKey} style={{ marginBottom: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5, font: '600 12px var(--font-ui)', color: 'var(--text-primary)' }}>
                <span>{AGENT_LABELS[c.agentKey]}</span>
                <span>{c.costUsd === null ? (c.tokens / 1e6).toFixed(2) + 'M tok' : fmtMoney(c.costUsd)}</span>
              </div>
              <div style={{ height: 6, borderRadius: 4, background: 'rgba(255,255,255,0.06)', overflow: 'hidden' }}>
                <div style={{ width: (c.costUsd === null ? 30 : Math.min(100, (c.costUsd / 62) * 100)) + '%', height: '100%', background: 'var(--accent)' }} />
              </div>
              {c.costUsd === null && <div style={{ font: '500 10.5px var(--font-ui)', color: 'var(--status-warning)', marginTop: 3 }}>subscription — dollar cost not reported</div>}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
