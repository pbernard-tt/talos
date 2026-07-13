Horizontal run-lifecycle progress. Map the run's real status to each step's state — do not show a step as RUNNING and COMPLETED at once, and mark every step after a FAILED one as SKIPPED.

```jsx
<StatusTimeline
  steps={[
    { key: "workspace", label: "Workspace prep", state: "COMPLETED" },
    { key: "agent", label: "Agent execution", state: "RUNNING" },
    { key: "tests", label: "Test execution", state: "PENDING" },
  ]}
/>
```
