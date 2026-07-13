The draggable Kanban card. A priority dot (top-right), optional live-run indicator, optional blocked-reason line, then agent + risk badges along the bottom.

```jsx
<TaskCard
  task={{ title: "Fix rounding error in tax line", projectName: "Ledgerly API", priority: "HIGH", agentKey: "claude-code", riskLevel: "HIGH" }}
  onClick={openDrawer}
  onDragStart={handleDragStart}
/>
```
