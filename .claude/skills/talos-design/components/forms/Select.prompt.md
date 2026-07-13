Native select for filter toolbars. Always include an explicit "All …" option as the first entry when used as a filter.

```jsx
<Select
  value={filter}
  onChange={e => setFilter(e.target.value)}
  options={[{ value: "all", label: "All projects" }, { value: "p-1", label: "Ledgerly API" }]}
/>
```
