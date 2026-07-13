function Login({ onSuccess }) {
  const [email, setEmail] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [showPassword, setShowPassword] = React.useState(false);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState(null);
  const [attempts, setAttempts] = React.useState(0);
  const [lockUntil, setLockUntil] = React.useState(0);
  const [, forceTick] = React.useState(0);

  React.useEffect(() => {
    if (!lockUntil) return;
    const t = setInterval(() => forceTick((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, [lockUntil]);

  const locked = lockUntil && Date.now() < lockUntil;

  function submit(e) {
    e.preventDefault();
    if (locked) return;
    setLoading(true);
    setError(null);
    setTimeout(() => {
      const ok = email.trim().toLowerCase() === 'paul@ledgerly.dev' && password === 'talos-demo';
      if (ok) {
        setLoading(false);
        onSuccess();
      } else {
        const next = attempts + 1;
        setAttempts(next);
        setLoading(false);
        if (next >= 3) {
          setLockUntil(Date.now() + 20000);
          setError('locked');
        } else {
          setError('invalid');
        }
      }
    }, 600);
  }

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'radial-gradient(1200px 600px at 20% 10%, rgba(139,92,246,0.08), transparent), radial-gradient(900px 500px at 85% 90%, rgba(56,189,248,0.06), transparent), var(--surface-app)', position: 'relative' }}>
      <div style={{ width: 400, position: 'relative', zIndex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 32 }}>
          <img src="../../assets/talos-logo.png" alt="Talos" style={{ width: 34, height: 34, borderRadius: 9, objectFit: 'cover' }} />
          <div style={{ font: '700 17px var(--font-ui)', letterSpacing: '-0.01em', color: 'var(--text-primary)' }}>Talos</div>
        </div>
        <div style={{ background: 'var(--surface-card)', border: '1px solid var(--border-default)', borderRadius: 'var(--r-xl)', padding: 32, boxShadow: 'var(--shadow-3)' }}>
          <div style={{ font: '600 20px/1.3 var(--font-ui)', color: 'var(--text-primary)', marginBottom: 6 }}>Sign in to Talos</div>
          <div style={{ font: '400 13px/1.5 var(--font-ui)', color: 'var(--text-secondary)', marginBottom: 26 }}>
            Agent-agnostic development control plane. Orchestrate coding agents, review changes, and gate deploys from one place.
          </div>
          <form onSubmit={submit}>
            <div style={{ marginBottom: 16 }}>
              <label style={{ display: 'block', font: '600 12px var(--font-ui)', color: 'var(--text-secondary)', marginBottom: 6 }}>Email</label>
              <Input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@company.com" />
            </div>
            <div style={{ marginBottom: 8 }}>
              <label style={{ display: 'block', font: '600 12px var(--font-ui)', color: 'var(--text-secondary)', marginBottom: 6 }}>Password</label>
              <div style={{ position: 'relative' }}>
                <Input type={showPassword ? 'text' : 'password'} value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
                <button type="button" onClick={() => setShowPassword((s) => !s)} style={{ position: 'absolute', right: 6, top: 6, bottom: 6, width: 30, background: 'none', border: 0, color: 'var(--text-muted)', cursor: 'pointer' }}>
                  {showPassword ? 'hide' : 'show'}
                </button>
              </div>
            </div>
            <div style={{ minHeight: 20, margin: '8px 0 4px' }}>
              {error === 'invalid' && <div style={{ font: '500 12.5px var(--font-ui)', color: 'var(--status-error)' }}>Incorrect email or password.</div>}
              {locked && <div style={{ font: '500 12.5px var(--font-ui)', color: 'var(--status-warning)' }}>Too many attempts. Try again in {Math.ceil((lockUntil - Date.now()) / 1000)}s.</div>}
            </div>
            <Button variant="primary" disabled={loading || locked} size="lg">
              <span style={{ width: '100%', textAlign: 'center', display: 'block' }}>{locked ? 'Locked' : loading ? 'Signing in…' : 'Sign in'}</span>
            </Button>
          </form>
          <div style={{ marginTop: 18, font: '400 11.5px var(--font-mono)', color: 'var(--text-muted)' }}>demo credentials: paul@ledgerly.dev / talos-demo</div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 20, justifyContent: 'center' }}>
          <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--status-success)' }} />
          <span style={{ font: '500 12px var(--font-ui)', color: 'var(--text-muted)' }}>All systems operational</span>
        </div>
      </div>
    </div>
  );
}
