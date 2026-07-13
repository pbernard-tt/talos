A tone-colored status pill with a small dot. Use for task status, run status, approval status, deploy status, test status — anywhere an enum value needs a quiet, scannable color signal.

```jsx
<StatusBadge label="WAITING_APPROVAL" tone="purple" />
<StatusBadge label="FAILED" tone="error" />
<StatusBadge label="COMPLETED" tone="success" />
```

Tone guide: `success` = terminal-good (COMPLETED, SUCCEEDED, connected, active), `error` = terminal-bad (FAILED, REJECTED, disconnected), `warning` = needs attention (BLOCKED, action-required, CHANGES_REQUESTED), `purple` = in-governance (WAITING_APPROVAL, RUNNING_*), `info` = queued/neutral-active (QUEUED, PREPARING_WORKSPACE), `neutral` = inert (CANCELLED, unknown).
