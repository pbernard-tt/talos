Single-line text input on the sunken (`--surface-sunken`) background so it reads as a well inside a card, not a floating field.

```jsx
<Input value={title} onChange={e => setTitle(e.target.value)} placeholder="Task title" />
<Input value={notes} onChange={setNotes} error="Notes are required for this decision." />
```
