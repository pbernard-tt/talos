# talos-whatsapp-adapter

Chat-based task intake and notifications for Talos via the WhatsApp Business Cloud API (Section 16
Phase 12 Track B), the second implementation of the same command schema as `apps/telegram-adapter`.

Unlike Telegram, the Cloud API only supports webhooks (no long-poll option), so this adapter is a
small FastAPI service:

- `GET /webhook` -- Meta's verification handshake (`hub.mode`/`hub.verify_token`/`hub.challenge`).
- `POST /webhook` -- inbound messages, signature-verified (`X-Hub-Signature-256` over the raw body
  against the app secret) before anything else runs. For messages from an allow-listed WhatsApp ID
  only, calls the public `talos-api` REST surface with a dedicated, scope-restricted service-account
  JWT to create tasks (`source=WHATSAPP`), check task/run status, and list pending approvals.
- consumes `approval.requested` / `pr.created` / `run.status.changed` off its own quorum queue on
  the `talos.events` exchange and sends a WhatsApp message with a dashboard deep link.

Approval *decisions* are never available from chat -- only the dashboard can approve/reject/deploy.

## Run

```bash
uv sync
uv run pytest
uv run talos-whatsapp-adapter
```

See Appendix A / `infra/docker-compose.dev.yml` for required `TALOS_*` environment variables.
