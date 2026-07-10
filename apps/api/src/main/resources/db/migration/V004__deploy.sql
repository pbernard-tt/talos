-- Phase 10 (Dokploy integration): project_environments was explicitly deferred to this phase
-- (Section 9.4). approvals.environment lets a DEPLOY-type approval remember which environment it
-- is for (see docs/phase-reports/phase-10-report.md).
CREATE TABLE project_environments (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id),
  environment VARCHAR(50) NOT NULL,
  provider VARCHAR(30) NOT NULL DEFAULT 'dokploy',
  app_id VARCHAR(200) NOT NULL,
  approval_required BOOLEAN NOT NULL DEFAULT true,
  last_deploy_status VARCHAR(20) CHECK (last_deploy_status IN ('RUNNING','SUCCEEDED','FAILED')),
  last_deployed_at TIMESTAMPTZ,
  last_run_id UUID REFERENCES agent_runs(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (project_id, environment)
);

ALTER TABLE approvals ADD COLUMN environment VARCHAR(50);
