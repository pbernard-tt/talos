Modal shell used for every consequential confirmation. Put a plain-language `consequence` sentence above the body, and the actual action buttons (usually a `Button` pair) as `children`.

```jsx
<Dialog
  open={confirmOpen}
  onClose={close}
  title="Approve this run?"
  consequence="Talos will commit, push the branch, and open a pull request. This cannot be undone."
>
  <Button variant="primary" onClick={approve}>Approve &amp; push</Button>
</Dialog>
```
