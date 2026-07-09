# Event JSON Schemas

Every message on the `talos.events` exchange uses the envelope from Section 11 of the implementation plan:

```json
{
  "event_id": "uuid-v7",
  "event_type": "task.run.requested",
  "occurred_at": "2026-07-09T12:00:00Z",
  "version": 1,
  "payload": {}
}
```

One JSON Schema file per event type lives here, named `<event_type>.json` (e.g. `task.run.requested.json`), validated by both the producer (talos-api) and consumer (talos-orchestrator) test suites.

No event types are implemented yet — `task.run.requested` is the first, added in Phase 5.
