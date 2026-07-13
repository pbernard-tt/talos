Flags a HIGH risk task or policy-flagged change. Renders `null` for NORMAL — do not render an empty/muted badge for the normal case.

```jsx
<RiskBadge level="HIGH" />
{/* level="NORMAL" renders nothing — that is correct, not a bug */}
```
