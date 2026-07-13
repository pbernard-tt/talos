One grid row in the Agent Runs list. Column order is fixed: run id, task, project, agent, auth mode, duration, cost, status.

```jsx
<RunRow
  run={{ id: "r-2", taskTitle: "Invoice detail page: add PDF download", projectName: "Ledgerly Web", agentKey: "codex-cli", authMode: "api_key", duration: "10h 32m", cost: "$0.61", status: "WAITING_APPROVAL", statusTone: "purple" }}
  onClick={() => openRun("r-2")}
/>
```
