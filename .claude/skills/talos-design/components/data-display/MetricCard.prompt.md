One number, one label, clickable through to the filtered view it summarizes. Never decorative — every metric on Command Center is a real live count.

```jsx
<MetricCard label="Pending approvals" value={4} color="var(--accent-soft)" onClick={() => goTo("reviews")} />
<MetricCard label="Failed runs" value={2} color="var(--status-error)" onClick={() => goTo("runs")} />
```
