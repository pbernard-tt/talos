# talos-telegram-adapter

Chat-based task intake and notifications for Talos (Section 16 Phase 12 Track B).

An ordinary REST client plus a notifier -- not a new communication path (Section 4.3). It:

- long-polls the Telegram Bot API and, for messages from an allow-listed operator chat ID only,
  calls the public `talos-api` REST surface (`/api/v1/**`) with a dedicated, scope-restricted
  service-account JWT to create tasks (`source=TELEGRAM`), check task/run status, and list pending
  approvals;
- consumes `approval.requested` / `pr.created` / `run.status.changed` off its own quorum queue on
  the `talos.events` exchange and sends a chat notification with a dashboard deep link.

Approval *decisions* are never available from chat -- only the dashboard can approve/reject/deploy.

## Run

```bash
uv sync
uv run pytest
uv run talos-telegram-adapter
```

See Appendix A / `infra/docker-compose.dev.yml` for required `TALOS_*` environment variables.
