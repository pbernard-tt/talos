The single button primitive. `primary` (solid purple) for the one main action per view/dialog; `secondary` (bordered) for equal-weight alternatives; `ghost` (text-only) for tertiary/inline actions; `danger` (red outline) for reject/cancel/rollback.

```jsx
<Button variant="primary">Approve &amp; push</Button>
<Button variant="secondary">Retry</Button>
<Button variant="danger">Reject</Button>
<Button variant="ghost" size="sm">View all →</Button>
```

Never use more than one `primary` button in the same dialog or toolbar.
