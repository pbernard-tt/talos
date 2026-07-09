# packages/contracts

Source of truth for every cross-service contract in Talos.

- `openapi.yaml` — talos-api's public (`/api/v1`) and internal (`/internal/v1`) REST surface. The Angular client is generated from this file; CI diffs the running API's springdoc output against it.
- `events/` — one JSON Schema per RabbitMQ event type published on the `talos.events` exchange (Section 11).
- `runner-api.yaml` — the runner supervisor's HTTP contract (Section 10.5), added in Phase 6.

Endpoints, events, and schemas are added phase by phase as each is implemented — this package holds no speculative contracts ahead of the phase that needs them.
