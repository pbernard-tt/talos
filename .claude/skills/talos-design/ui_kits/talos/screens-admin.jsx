function TeamScreen({ data }) {
  const { team } = data;
  return (
    <div style={{ maxWidth: 1000 }}>
      <SectionTitle title="Team" subtitle="Owner controls integrations, secrets, and deploys. Reviewer approves. Maintainer runs agents. Viewer reads." />
      <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-xl)', overflow: 'hidden' }}>
        {team.map((t) => (
          <div key={t.id} style={{ display: 'grid', gridTemplateColumns: '1.6fr 0.8fr 0.8fr 0.8fr', gap: 10, padding: '13px 16px', borderTop: '1px solid var(--border-default)', alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
              <div style={{ width: 30, height: 30, borderRadius: 8, background: 'var(--accent-tint-strong)', color: 'var(--accent-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', font: '700 11px var(--font-mono)', flex: 'none' }}>{t.initials}</div>
              <div style={{ minWidth: 0 }}>
                <div style={{ font: '600 13px var(--font-ui)', color: 'var(--text-primary)', whiteSpace: 'nowrap' }}>{t.name}</div>
                <div style={{ font: '400 11px var(--font-ui)', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>{t.email}</div>
              </div>
            </div>
            <div style={{ font: '600 11.5px var(--font-ui)', color: 'var(--text-secondary)' }}>{t.role}</div>
            <StatusBadge label={t.status} tone={t.status === 'active' ? 'success' : 'warning'} />
            <div style={{ font: '500 11.5px var(--font-ui)', color: 'var(--text-muted)' }}>{t.lastActive ? fmtAgo(t.lastActive) : 'Never'}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function AuditScreen({ data }) {
  const { auditEvents } = data;
  const panels = [
    { title: 'Runner isolation', detail: 'Non-root execution, no Docker socket, per-run resource limits.' },
    { title: 'Secret masking', detail: 'Every log path replaces injected secret values with *** before persistence.' },
    { title: 'Workspace retention', detail: 'Terminal workspaces older than 7 days are cleaned up, unless a PR is open.' },
  ];
  return (
    <div style={{ maxWidth: 1300 }}>
      <SectionTitle title="Audit & Security" subtitle="A searchable record of every consequential action, plus the security posture behind it." />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10, marginBottom: 20 }}>
        {panels.map((p) => (
          <div key={p.title} style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 13 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 6 }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--status-success)' }} />
              <span style={{ font: '700 12px var(--font-ui)', color: 'var(--text-primary)' }}>{p.title}</span>
            </div>
            <div style={{ font: '400 11.5px/1.5 var(--font-ui)', color: 'var(--text-muted)' }}>{p.detail}</div>
          </div>
        ))}
      </div>
      <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-xl)', overflow: 'hidden' }}>
        {auditEvents.map((a) => (
          <div key={a.id} style={{ display: 'grid', gridTemplateColumns: '1.1fr 1.3fr 1.3fr 0.9fr 0.9fr', gap: 10, padding: '11px 16px', borderTop: '1px solid var(--border-default)', alignItems: 'center' }}>
            <span style={{ font: '500 12px var(--font-ui)', color: 'var(--text-primary)' }}>{a.actor}</span>
            <span style={{ font: '500 11.5px var(--font-mono)', color: 'var(--text-secondary)' }}>{a.action}</span>
            <span style={{ font: '400 11.5px var(--font-ui)', color: 'var(--text-muted)' }}>{a.resource}</span>
            <span style={{ font: '400 11.5px var(--font-ui)', color: 'var(--text-muted)' }}>{fmtAgo(a.createdAt)}</span>
            <StatusBadge label={a.result} tone={a.result === 'success' ? 'success' : a.result === 'failure' || a.result === 'blocked' ? 'error' : 'warning'} />
          </div>
        ))}
      </div>
    </div>
  );
}

function SystemScreen({ data }) {
  const { systemServices } = data;
  return (
    <div style={{ maxWidth: 1200 }}>
      <SectionTitle title="System Health" subtitle="Every service in the deployment, with the latest health check." />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 12 }}>
        {systemServices.map((sv) => (
          <div key={sv.id} style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 15 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
              <div style={{ font: '700 13px var(--font-mono)', color: 'var(--text-primary)' }}>{sv.name}</div>
              <StatusBadge label={sv.status === 'unknown' ? 'not configured' : sv.status} tone={sv.status === 'healthy' ? 'success' : sv.status === 'unknown' ? 'neutral' : 'warning'} />
            </div>
            <div style={{ font: '500 11.5px var(--font-ui)', color: 'var(--text-muted)', marginBottom: 8 }}>{sv.kind}</div>
            <div style={{ display: 'flex', justifyContent: 'space-between', font: '500 11.5px var(--font-ui)', color: 'var(--text-secondary)' }}>
              <span>{sv.latencyMs !== null ? sv.latencyMs + 'ms' : '—'}</span>
            </div>
            {sv.extra && <div style={{ marginTop: 8, font: '500 11px var(--font-ui)', color: 'var(--text-muted)' }}>{sv.extra}</div>}
          </div>
        ))}
      </div>
    </div>
  );
}

function SettingsScreen({ data }) {
  const { user } = data;
  const [digest, setDigest] = React.useState(true);
  const [chat, setChat] = React.useState(true);
  return (
    <div style={{ maxWidth: 600 }}>
      <SectionTitle title="Settings" />
      <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 18, marginBottom: 14 }}>
        <div style={{ font: '700 13.5px var(--font-ui)', marginBottom: 14, color: 'var(--text-primary)' }}>Profile</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 40, height: 40, borderRadius: 10, background: 'var(--accent-tint-strong)', color: 'var(--accent-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', font: '700 14px var(--font-mono)' }}>{user.initials}</div>
          <div><div style={{ font: '700 14px var(--font-ui)', color: 'var(--text-primary)' }}>{user.name}</div><div style={{ font: '400 12px var(--font-ui)', color: 'var(--text-muted)' }}>{user.email}</div></div>
          <div style={{ marginLeft: 'auto', font: '700 11px var(--font-ui)', color: 'var(--accent-soft)', background: 'var(--accent-tint-strong)', padding: '4px 10px', borderRadius: 6 }}>{user.role}</div>
        </div>
      </div>
      <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-lg)', padding: 18 }}>
        <div style={{ font: '700 13.5px var(--font-ui)', marginBottom: 14, color: 'var(--text-primary)' }}>Notifications</div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0' }}>
          <span style={{ font: '500 12.5px var(--font-ui)', color: 'var(--text-primary)' }}>Email digest for pending approvals</span>
          <button onClick={() => setDigest((d) => !d)} style={{ width: 40, height: 22, borderRadius: 11, background: digest ? 'var(--accent)' : 'var(--border-strong)', border: 0, cursor: 'pointer', position: 'relative' }}>
            <span style={{ position: 'absolute', top: 2, left: digest ? 22 : 2, width: 18, height: 18, borderRadius: '50%', background: '#fff', transition: 'left 0.15s' }} />
          </button>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0' }}>
          <span style={{ font: '500 12.5px var(--font-ui)', color: 'var(--text-primary)' }}>Telegram / WhatsApp notifications</span>
          <button onClick={() => setChat((d) => !d)} style={{ width: 40, height: 22, borderRadius: 11, background: chat ? 'var(--accent)' : 'var(--border-strong)', border: 0, cursor: 'pointer', position: 'relative' }}>
            <span style={{ position: 'absolute', top: 2, left: chat ? 22 : 2, width: 18, height: 18, borderRadius: '50%', background: '#fff', transition: 'left 0.15s' }} />
          </button>
        </div>
      </div>
    </div>
  );
}
