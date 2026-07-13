function uid(prefix) { return prefix + '-' + Math.random().toString(36).slice(2, 9); }

function App() {
  const [authenticated, setAuthenticated] = React.useState(false);
  const [view, setView] = React.useState('command-center');
  const [projectId, setProjectId] = React.useState(null);
  const [runId, setRunId] = React.useState(null);
  const [reviewId, setReviewId] = React.useState(null);

  const [projects] = React.useState(PROJECTS);
  const [tasks, setTasks] = React.useState(TASKS);
  const [runs, setRuns] = React.useState(RUNS);
  const [approvals, setApprovals] = React.useState(APPROVALS);
  const [environments, setEnvironments] = React.useState(ENVIRONMENTS);
  const [dismissedRecs, setDismissedRecs] = React.useState([]);
  const [memoryProjectId, setMemoryProjectId] = React.useState(null);

  const [toasts, setToasts] = React.useState([]);
  const [taskDrawerId, setTaskDrawerId] = React.useState(null);
  const [newTaskOpen, setNewTaskOpen] = React.useState(false);
  const [startRunTaskId, setStartRunTaskId] = React.useState(null);
  const [reviewPending, setReviewPending] = React.useState(null);
  const [paletteOpen, setPaletteOpen] = React.useState(false);
  const [paletteQuery, setPaletteQuery] = React.useState('');

  React.useEffect(() => {
    function onKey(e) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setPaletteOpen(true); }
      if (e.key === 'Escape') setPaletteOpen(false);
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  function toast(message, tone) {
    const id = uid('toast');
    setToasts((t) => [...t, { id, message, tone }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4200);
  }

  function navigate(v) { setView(v); setPaletteOpen(false); }
  function openProject(id) { setProjectId(id); setView('project-detail'); }
  function openRun(id) { setRunId(id); setView('run-detail'); }
  function openReview(id) { setReviewId(id); setView('review-detail'); }
  function openReviewForRun(rid) { const a = approvals.find((x) => x.runId === rid); if (a) openReview(a.id); }
  function openTask(id) { setTaskDrawerId(id); }

  function requestStartRun(taskId) { setTaskDrawerId(null); setStartRunTaskId(taskId); }
  function confirmStartRun(taskId, agentKey, authMode) {
    const task = tasks.find((t) => t.id === taskId);
    const slug = task.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').slice(0, 24);
    const run = { id: uid('r'), taskId, projectId: task.projectId, status: 'PREPARING_WORKSPACE', agentKey, providerAuthMode: authMode, branchName: 'agent/task-' + taskId + '-' + slug, currentStep: 'WORKSPACE', startedAt: new Date().toISOString(), testStatus: 'NOT_RUN', reviewStatus: 'CLEAN' };
    setRuns((r) => [run, ...r]);
    setTasks((ts) => ts.map((t) => (t.id === taskId ? { ...t, status: 'RUNNING' } : t)));
    setStartRunTaskId(null);
    toast('Run queued for “' + task.title + '”.', 'purple');
  }
  function moveTask(taskId, status) {
    const task = tasks.find((t) => t.id === taskId);
    setTasks((ts) => ts.map((t) => (t.id === taskId ? { ...t, status } : t)));
    toast('Moved “' + task.title + '” to ' + status + '.', 'success');
  }
  function createTask({ projectId, title, description }) {
    setTasks((ts) => [{ id: uid('t'), projectId, title, description, status: 'BACKLOG', priority: 'MEDIUM', riskLevel: 'NORMAL', agentKey: 'claude-code' }, ...ts]);
    setNewTaskOpen(false);
    toast('Task created in Backlog.', 'success');
  }
  function cancelRun(id) {
    const run = runs.find((r) => r.id === id);
    setRuns((rs) => rs.map((r) => (r.id === id ? { ...r, status: 'CANCELLED' } : r)));
    if (run) setTasks((ts) => ts.map((t) => (t.id === run.taskId ? { ...t, status: 'BACKLOG' } : t)));
    toast('Run cancelled.', 'warning');
  }
  function retryRun(id) {
    const run = runs.find((r) => r.id === id);
    const clone = { ...run, id: uid('r'), status: 'PREPARING_WORKSPACE', currentStep: 'WORKSPACE', startedAt: new Date().toISOString(), completedAt: null, errorMessage: null, testStatus: 'NOT_RUN' };
    setRuns((rs) => [clone, ...rs]);
    setTasks((ts) => ts.map((t) => (t.id === run.taskId ? { ...t, status: 'RUNNING' } : t)));
    toast('Retry queued as a new run.', 'purple');
  }
  function decide(approvalId, type) { setReviewPending({ id: approvalId, type }); }
  function submitDecision(approvalId, type, notes) {
    const approval = approvals.find((a) => a.id === approvalId);
    if (!approval) return;
    if (type === 'approve') {
      setRuns((rs) => rs.map((r) => (r.id === approval.runId ? { ...r, status: 'COMPLETED' } : r)));
      setTasks((ts) => ts.map((t) => (t.id === approval.taskId ? { ...t, status: 'DONE' } : t)));
      setApprovals((as) => as.map((a) => (a.id === approvalId ? { ...a, status: 'APPROVED' } : a)));
      toast('Approved. Talos will push the branch and open a pull request.', 'success');
    } else if (type === 'reject') {
      setRuns((rs) => rs.map((r) => (r.id === approval.runId ? { ...r, status: 'REJECTED' } : r)));
      setTasks((ts) => ts.map((t) => (t.id === approval.taskId ? { ...t, status: 'READY' } : t)));
      setApprovals((as) => as.map((a) => (a.id === approvalId ? { ...a, status: 'REJECTED' } : a)));
      toast('Rejected. Task returned to Ready.', 'warning');
    } else {
      setRuns((rs) => rs.map((r) => (r.id === approval.runId ? { ...r, status: 'REJECTED' } : r)));
      setTasks((ts) => ts.map((t) => (t.id === approval.taskId ? { ...t, status: 'READY' } : t)));
      setApprovals((as) => as.map((a) => (a.id === approvalId ? { ...a, status: 'CHANGES_REQUESTED' } : a)));
      toast('Changes requested. Task returned to Ready with your notes.', 'warning');
    }
    setReviewPending(null);
  }
  function dismissRec(id) { setDismissedRecs((d) => [...d, id]); }
  function deploy(envId) {
    setEnvironments((es) => es.map((e) => (e.id === envId ? { ...e, lastDeployStatus: 'RUNNING' } : e)));
    toast('Deployment triggered.', 'purple');
    setTimeout(() => {
      setEnvironments((es) => es.map((e) => (e.id === envId ? { ...e, lastDeployStatus: 'SUCCEEDED', lastDeployedAt: new Date().toISOString() } : e)));
      toast('Deployment succeeded.', 'success');
    }, 2200);
  }
  function rollback(envId) {
    setEnvironments((es) => es.map((e) => (e.id === envId ? { ...e, lastDeployStatus: 'RUNNING' } : e)));
    toast('Rolling back to previous image tag…', 'warning');
    setTimeout(() => {
      setEnvironments((es) => es.map((e) => (e.id === envId ? { ...e, lastDeployStatus: 'SUCCEEDED', lastDeployedAt: new Date().toISOString() } : e)));
      toast('Rollback complete.', 'success');
    }, 1800);
  }

  if (!authenticated) return <Login onSuccess={() => setAuthenticated(true)} />;

  const drawerTask = taskDrawerId ? tasks.find((t) => t.id === taskDrawerId) : null;
  const drawerProject = drawerTask ? projects.find((p) => p.id === drawerTask.projectId) : null;
  const startRunTask = startRunTaskId ? tasks.find((t) => t.id === startRunTaskId) : null;
  const startRunProject = startRunTask ? projects.find((p) => p.id === startRunTask.projectId) : null;

  const currentProject = projectId ? projects.find((p) => p.id === projectId) : null;
  const currentRun = runId ? runs.find((r) => r.id === runId) : null;
  const currentRunTask = currentRun ? tasks.find((t) => t.id === currentRun.taskId) : null;
  const currentRunProject = currentRun ? projects.find((p) => p.id === currentRun.projectId) : null;
  const currentApproval = reviewId ? approvals.find((a) => a.id === reviewId) : null;
  const currentApprovalRun = currentApproval ? runs.find((r) => r.id === currentApproval.runId) : null;
  const currentApprovalTask = currentApproval ? tasks.find((t) => t.id === currentApproval.taskId) : null;
  const currentApprovalProject = currentApproval ? projects.find((p) => p.id === currentApproval.projectId) : null;

  const breadcrumbMap = { 'command-center': 'Command Center', projects: 'Projects', 'project-detail': currentProject ? currentProject.name : '', board: 'Task Board', runs: 'Agent Runs', 'run-detail': currentRun ? currentRun.id : '', reviews: 'Review Center', 'review-detail': currentApproval ? currentApproval.id : '', deployments: 'Deployments', memory: 'Memory & Docs', costs: 'Costs & Insights', integrations: 'Integrations', team: 'Team', audit: 'Audit & Security', system: 'System Health', settings: 'Settings' };
  const counts = { board: tasks.filter((t) => t.status === 'BLOCKED').length || undefined, reviews: approvals.filter((a) => a.status === 'PENDING').length || undefined };

  const pages = [
    ['command-center', 'Command Center'], ['projects', 'Projects'], ['board', 'Task Board'], ['runs', 'Agent Runs'],
    ['reviews', 'Review Center'], ['deployments', 'Deployments'], ['memory', 'Memory & Docs'], ['costs', 'Costs & Insights'],
    ['integrations', 'Integrations'], ['team', 'Team'], ['audit', 'Audit & Security'], ['system', 'System Health'], ['settings', 'Settings'],
  ];
  const q = paletteQuery.toLowerCase().trim();
  const paletteGroups = [
    { label: 'Pages', items: pages.filter(([, l]) => !q || l.toLowerCase().includes(q)).map(([key, label]) => ({ title: label, onClick: () => { navigate(key); setPaletteOpen(false); } })) },
    { label: 'Projects', items: projects.filter((p) => !q || p.name.toLowerCase().includes(q)).slice(0, 5).map((p) => ({ title: p.name, onClick: () => { openProject(p.id); setPaletteOpen(false); } })) },
  ];

  const screenData = { projects, tasks, runs, approvals, environments, integrations: INTEGRATIONS, team: TEAM, auditEvents: AUDIT_EVENTS, systemServices: SYSTEM_SERVICES, user: CURRENT_USER, dismissedRecs, memoryProjectId };
  const screenActions = { navigate, openProject, openRun, openReview, openReviewForRun, openTask, dismissRec, toast, requestStartRun, moveTask, newTask: () => setNewTaskOpen(true), decide, deploy, rollback, setMemoryProject: setMemoryProjectId };

  let screen = null;
  if (view === 'command-center') screen = <CommandCenterScreen data={screenData} actions={screenActions} />;
  else if (view === 'projects') screen = <ProjectsListScreen data={screenData} actions={screenActions} />;
  else if (view === 'project-detail') screen = <ProjectDetailScreen data={{ ...screenData, project: currentProject }} actions={screenActions} />;
  else if (view === 'board') screen = <TaskBoardScreen data={screenData} actions={screenActions} />;
  else if (view === 'runs') screen = <RunsListScreen data={screenData} actions={screenActions} />;
  else if (view === 'run-detail') screen = <RunDetailScreen data={{ run: currentRun, task: currentRunTask, project: currentRunProject }} actions={screenActions} />;
  else if (view === 'reviews') screen = <ReviewsListScreen data={screenData} actions={screenActions} />;
  else if (view === 'review-detail') screen = <ReviewDetailScreen data={{ approval: currentApproval, run: currentApprovalRun, task: currentApprovalTask, project: currentApprovalProject }} actions={screenActions} />;
  else if (view === 'deployments') screen = <DeploymentsScreen data={screenData} actions={screenActions} />;
  else if (view === 'integrations') screen = <IntegrationsScreen data={screenData} />;
  else if (view === 'memory') screen = <MemoryScreen data={screenData} actions={screenActions} />;
  else if (view === 'costs') screen = <CostsScreen data={screenData} />;
  else if (view === 'team') screen = <TeamScreen data={screenData} />;
  else if (view === 'audit') screen = <AuditScreen data={screenData} />;
  else if (view === 'system') screen = <SystemScreen data={screenData} />;
  else if (view === 'settings') screen = <SettingsScreen data={screenData} />;

  return (
    <React.Fragment>
      <Shell
        view={view}
        onNavigate={navigate}
        breadcrumb={breadcrumbMap[view]}
        counts={counts}
        onOpenPalette={() => setPaletteOpen(true)}
        onNewTask={() => setNewTaskOpen(true)}
        onLogout={() => setAuthenticated(false)}
        toasts={toasts}
      >
        {screen}
      </Shell>
      <TaskDrawer task={drawerTask} project={drawerProject} runs={runs} onClose={() => setTaskDrawerId(null)} onStartRun={requestStartRun} onOpenRun={(id) => { setTaskDrawerId(null); openRun(id); }} />
      <NewTaskDialog open={newTaskOpen} onClose={() => setNewTaskOpen(false)} projects={projects} onCreate={createTask} />
      <StartRunDialog task={startRunTask} project={startRunProject} onClose={() => setStartRunTaskId(null)} onConfirm={confirmStartRun} />
      <ReviewDecisionDialog pending={reviewPending} onClose={() => setReviewPending(null)} onSubmit={submitDecision} />
      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} query={paletteQuery} onQuery={setPaletteQuery} groups={paletteGroups} />
    </React.Fragment>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
