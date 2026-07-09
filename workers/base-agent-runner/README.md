# workers/base-agent-runner

Base Docker image for per-run agent execution: non-root user, no Docker socket, cgroup resource limits. Built in Phase 11, when the runner supervisor moves adapter execution from local subprocesses into per-run containers (Section 8, Section 12.1) without changing the runner's HTTP contract.
