const NAV_ITEMS = [
  { key: 'command-center', label: 'Command Center' },
  { key: 'projects', label: 'Projects' },
  { key: 'board', label: 'Task Board' },
  { key: 'runs', label: 'Agent Runs' },
  { key: 'reviews', label: 'Review Center' },
  { key: 'deployments', label: 'Deployments' },
  { key: 'memory', label: 'Memory & Docs' },
  { key: 'costs', label: 'Costs & Insights' },
  { key: 'integrations', label: 'Integrations' },
  { key: 'team', label: 'Team' },
  { key: 'audit', label: 'Audit & Security' },
  { key: 'system', label: 'System Health' },
  { key: 'settings', label: 'Settings' },
];

function Shell({ view, onNavigate, breadcrumb, counts, onOpenPalette, onNewTask, onLogout, toasts, children }) {
  const [collapsed, setCollapsed] = React.useState(false);
  return (
    <div style={{ width: '100%', height: '100%', display: 'flex' }}>
      <div style={{ width: collapsed ? 64 : 244, flex: 'none', background: 'var(--surface-sunken)', borderRight: '1px solid var(--border-default)', display: 'flex', flexDirection: 'column', transition: 'width 0.16s ease-out' }}>
        <div style={{ height: 56, flex: 'none', display: 'flex', alignItems: 'center', gap: 10, padding: '0 16px', borderBottom: '1px solid var(--border-default)' }}>
          <img src="../../assets/talos-logo.png" alt="Talos" style={{ width: 28, height: 28, borderRadius: 8, objectFit: 'cover', flex: 'none' }} />
          {!collapsed && <div style={{ font: '700 15px var(--font-ui)', letterSpacing: '-0.01em', color: 'var(--text-primary)', whiteSpace: 'nowrap' }}>Talos</div>}
        </div>
        <div style={{ flex: 1, overflowY: 'auto', padding: '10px 8px', display: 'flex', flexDirection: 'column', gap: 2 }}>
          {NAV_ITEMS.map((item) => {
            const active = item.key === view || (item.key === 'projects' && view === 'project-detail') || (item.key === 'runs' && view === 'run-detail') || (item.key === 'reviews' && view === 'review-detail');
            const badge = counts[item.key];
            return (
              <button
                key={item.key}
                title={item.label}
                onClick={() => onNavigate(item.key)}
                style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '9px 10px', borderRadius: 8, border: 0, cursor: 'pointer', textAlign: 'left', background: active ? 'var(--accent-tint-strong)' : 'transparent', color: active ? 'var(--text-primary)' : 'var(--text-secondary)' }}
              >
                <span style={{ width: 6, height: 6, borderRadius: '50%', background: active ? 'var(--accent)' : 'var(--border-strong)', flex: 'none' }} />
                {!collapsed && <span style={{ font: '600 13px var(--font-ui)', flex: 1, whiteSpace: 'nowrap' }}>{item.label}</span>}
                {!collapsed && badge ? <span style={{ font: '700 10px var(--font-mono)', color: 'var(--status-warning)', background: 'var(--status-warning-tint)', padding: '1px 6px', borderRadius: 5 }}>{badge}</span> : null}
              </button>
            );
          })}
        </div>
        <div style={{ flex: 'none', padding: 10, borderTop: '1px solid var(--border-default)' }}>
          <button onClick={() => setCollapsed((c) => !c)} style={{ width: '100%', padding: 8, borderRadius: 8, border: '1px solid var(--border-default)', background: 'var(--surface-card)', color: 'var(--text-secondary)', cursor: 'pointer', font: '600 12px var(--font-ui)' }}>
            {collapsed ? '»' : '« Collapse'}
          </button>
        </div>
      </div>

      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', height: '100%' }}>
        <div style={{ height: 56, flex: 'none', display: 'flex', alignItems: 'center', gap: 14, padding: '0 20px', borderBottom: '1px solid var(--border-default)' }}>
          <div style={{ font: '600 13px var(--font-ui)', color: 'var(--text-primary)', flex: 'none' }}>Talos <span style={{ color: 'var(--border-strong)' }}>/</span> {breadcrumb}</div>
          <button onClick={onOpenPalette} style={{ flex: '1 1 240px', minWidth: 0, maxWidth: 420, height: 36, display: 'flex', alignItems: 'center', gap: 8, background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 9, padding: '0 12px', color: 'var(--text-muted)', cursor: 'pointer' }}>
            <span style={{ font: '400 13px var(--font-ui)', flex: 1, textAlign: 'left' }}>Search projects, tasks, runs…</span>
            <span style={{ font: '600 10.5px var(--font-mono)', background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-default)', borderRadius: 5, padding: '2px 6px', color: 'var(--text-secondary)' }}>⌘K</span>
          </button>
          <div style={{ flex: 1 }} />
          <Button variant="primary" size="md" onClick={onNewTask}>+ New task</Button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, padding: '6px 11px', borderRadius: 9, background: 'var(--surface-card)', border: '1px solid var(--border-default)' }}>
            <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--status-success)' }} />
            <span style={{ font: '600 12px var(--font-ui)', color: 'var(--text-secondary)' }}>All systems normal</span>
          </div>
          <button onClick={onLogout} style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'none', border: 0, cursor: 'pointer' }}>
            <div style={{ width: 28, height: 28, borderRadius: 8, background: 'var(--accent-tint-strong)', color: 'var(--accent-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', font: '700 11px var(--font-mono)' }}>PB</div>
            <span style={{ textAlign: 'left' }}>
              <div style={{ font: '600 12.5px var(--font-ui)', color: 'var(--text-primary)', lineHeight: 1.25 }}>Paul Bernard</div>
              <div style={{ font: '500 10.5px var(--font-ui)', color: 'var(--text-muted)', lineHeight: 1.25 }}>OWNER · Sign out</div>
            </span>
          </button>
        </div>
        <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '26px 30px 60px' }}>{children}</div>
      </div>

      <div style={{ position: 'fixed', bottom: 20, right: 20, zIndex: 200, display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'flex-end' }}>
        {toasts.map((t) => (
          <Toast key={t.id} message={t.message} tone={t.tone} />
        ))}
      </div>
    </div>
  );
}
