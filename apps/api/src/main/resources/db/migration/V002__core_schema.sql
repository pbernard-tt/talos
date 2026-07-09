CREATE TABLE projects (
  id UUID PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  slug VARCHAR(100) NOT NULL UNIQUE,
  repo_url VARCHAR(500) NOT NULL,
  default_branch VARCHAR(200) NOT NULL DEFAULT 'main',
  stack_type VARCHAR(50) NOT NULL,              -- 'spring-boot','angular','python',...
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ARCHIVED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE project_configs (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id),
  config_yaml TEXT NOT NULL,
  parsed_json JSONB NOT NULL,
  version INT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (project_id, version)
);
-- application invariant: at most one is_active=true row per project

CREATE TABLE tasks (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id),
  title VARCHAR(300) NOT NULL,
  description TEXT,
  source VARCHAR(30) NOT NULL DEFAULT 'DASHBOARD',  -- DASHBOARD|WEBHOOK|TELEGRAM|...
  status VARCHAR(20) NOT NULL DEFAULT 'BACKLOG'
    CHECK (status IN ('BACKLOG','READY','RUNNING','REVIEW','BLOCKED','DONE','CANCELLED')),
  priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW','MEDIUM','HIGH')),
  risk_level VARCHAR(10) NOT NULL DEFAULT 'NORMAL' CHECK (risk_level IN ('NORMAL','HIGH')),
  board_position INT NOT NULL DEFAULT 0,             -- ordering within a Kanban column
  requested_by UUID REFERENCES users(id),
  assigned_agent_key VARCHAR(50),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_project_status ON tasks(project_id, status);

CREATE TABLE agent_runs (
  id UUID PRIMARY KEY,
  task_id UUID NOT NULL REFERENCES tasks(id),
  project_id UUID NOT NULL REFERENCES projects(id),
  status VARCHAR(30) NOT NULL DEFAULT 'CREATED'
    CHECK (status IN ('CREATED','QUEUED','PREPARING_WORKSPACE','RUNNING_AGENT',
      'RUNNING_TESTS','REVIEWING','WAITING_APPROVAL','APPROVED','REJECTED',
      'COMPLETED','FAILED','CANCELLED')),
  agent_key VARCHAR(50) NOT NULL,
  provider_auth_mode VARCHAR(30) NOT NULL DEFAULT 'api_key',
  prompt TEXT,
  branch_name VARCHAR(300),
  workspace_path VARCHAR(500),
  summary TEXT,
  test_status VARCHAR(20) NOT NULL DEFAULT 'NOT_RUN'
    CHECK (test_status IN ('NOT_RUN','PASSED','FAILED','ERROR')),
  review_status VARCHAR(20) NOT NULL DEFAULT 'CLEAN'
    CHECK (review_status IN ('CLEAN','RISK_FLAGGED')),
  error_message TEXT,
  exit_code INT,
  timeout_at TIMESTAMPTZ,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_runs_task ON agent_runs(task_id);
CREATE INDEX idx_runs_status ON agent_runs(status);

CREATE TABLE agent_run_steps (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES agent_runs(id),
  step_type VARCHAR(20) NOT NULL
    CHECK (step_type IN ('WORKSPACE','AGENT','TESTS','REVIEW','PUSH','PR','DEPLOY')),
  status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING','COMPLETED','FAILED','SKIPPED')),
  summary TEXT,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ
);

CREATE TABLE agent_run_logs (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES agent_runs(id),
  step_id UUID REFERENCES agent_run_steps(id),
  sequence BIGINT NOT NULL,
  stream VARCHAR(10) NOT NULL CHECK (stream IN ('STDOUT','STDERR','SYSTEM')),
  message TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (run_id, sequence)
);

CREATE TABLE approvals (
  id UUID PRIMARY KEY,
  task_id UUID NOT NULL REFERENCES tasks(id),
  run_id UUID NOT NULL REFERENCES agent_runs(id),
  approval_type VARCHAR(30) NOT NULL,           -- 'RUN_RESULT','DEPLOY',...
  requested_action VARCHAR(200) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','APPROVED','REJECTED','CHANGES_REQUESTED','EXPIRED')),
  requested_by UUID REFERENCES users(id),
  approved_by UUID REFERENCES users(id),
  approved_at TIMESTAMPTZ,
  notes TEXT,
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE git_changes (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES agent_runs(id),
  file_path VARCHAR(1000) NOT NULL,
  change_type VARCHAR(10) NOT NULL CHECK (change_type IN ('ADDED','MODIFIED','DELETED','RENAMED')),
  additions INT NOT NULL DEFAULT 0,
  deletions INT NOT NULL DEFAULT 0,
  risk_flagged BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE pull_requests (
  id UUID PRIMARY KEY,
  run_id UUID NOT NULL REFERENCES agent_runs(id),
  provider VARCHAR(20) NOT NULL DEFAULT 'github',
  pr_number INT,
  url VARCHAR(500),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','MERGED','CLOSED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE integrations (
  id UUID PRIMARY KEY,
  type VARCHAR(30) NOT NULL,                    -- 'github','dokploy','anthropic',...
  name VARCHAR(100) NOT NULL,
  config_json JSONB NOT NULL DEFAULT '{}',      -- non-secret settings only
  enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE secret_values (
  id UUID PRIMARY KEY,
  encrypted_value BYTEA NOT NULL,               -- AES-256-GCM via TALOS_SECRETS_KEY
  nonce BYTEA NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- read/written ONLY by dev.talos.secrets; never exposed via REST

CREATE TABLE integration_credentials (
  id UUID PRIMARY KEY,
  integration_id UUID NOT NULL REFERENCES integrations(id),
  secret_ref UUID NOT NULL REFERENCES secret_values(id),
  auth_mode VARCHAR(30) NOT NULL,               -- 'api_key','pat','deploy_key',...
  owner_user_id UUID REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
