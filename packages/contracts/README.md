# packages/contracts

Source of truth for every cross-service contract in Talos.

Copyright 2026 Vulkan Technologies.

- `openapi.yaml` — talos-api's public (`/api/v1`) and internal (`/internal/v1`) REST surface. The Angular client is generated from this file; CI diffs the running API's springdoc output against it.
- `events/` — one JSON Schema per RabbitMQ event type published on the `talos.events` exchange (Section 11).
- `runner-api.yaml` — the runner supervisor's HTTP contract (Section 10.5), added in Phase 6.
- `chat/` — the normalized inbound-command schema shared by every chat trigger adapter (`apps/telegram-adapter`, `apps/whatsapp-adapter`, Phase 12 Track B), so a new channel reuses the same command handling instead of growing the API surface per channel.

Endpoints, events, and schemas are added phase by phase as each is implemented — this package holds no speculative contracts ahead of the phase that needs them.

## Licensing

The Talos OpenAPI descriptions, event schemas, and other interface definitions in this package are
licensed under the [Apache License 2.0](LICENSE) (`Apache-2.0`). This package is an explicit
exception to the repository root's AGPL licence so clients and integrations may reuse the public
contracts under permissive terms.
