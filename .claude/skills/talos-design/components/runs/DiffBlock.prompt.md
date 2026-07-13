One changed file in the Review Center / Run Detail diff. Always show the risk callout text when `risk` is true — never just color the header without saying what matched.

```jsx
<DiffBlock file={{ path: "db/migrations/V014__cohort_retention_view.sql", additions: 26, deletions: 0, hunkText: "@@ -0,0 +1,26 @@\n+CREATE MATERIALIZED VIEW cohort_retention AS …", risk: true, riskLabel: "Matches policy pattern **/migrations/**" }} />
```
