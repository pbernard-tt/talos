# Ops runbook

Manual procedures for things that aren't (yet) automated, dashboarded, or alerted on. Started
2026-07-11 with the first entry below (review gap #11); add sections here as more come up rather
than scattering them across phase reports.

## Checking `talos.dlq` depth

Section 11: a `task.run.requested`/`run.cancel.requested`/`approval.decided` message that fails
processing 3 times (quorum queue `x-delivery-limit`) is dead-lettered to `talos.dlq` instead of
being retried forever. **Nothing consumes this queue** -- a poisoned message sits there silently
until a human looks. There is no scheduled alert; check it periodically, or whenever runs seem to
be getting silently dropped (a task stuck in `QUEUED` well past its timeout is the usual symptom).

**From the dashboard (fastest):** `GET /api/v1/dashboard/dlq-depth` (any authenticated role) --
the same passive queue lookup the Command Center would use if this were ever wired into a live
widget. Returns `{"depth": 0}` before the orchestrator has connected at all, which is normal on a
fresh install, not a sign of trouble.

**From RabbitMQ directly**, if the API is unreachable or you want the actual message bodies:

```bash
# depth only
docker exec talos-rabbitmq rabbitmqctl list_queues name messages | grep talos.dlq

# or via the management UI (dev compose publishes this on :15673)
open http://localhost:15673/#/queues/%2F/talos.dlq
```

The management UI's "Get messages" action lets you inspect (and optionally requeue) individual
dead-lettered payloads -- each is the original event envelope (`event_id`, `event_type`,
`occurred_at`, `payload`), so the `event_type` in the message body tells you which of the three
queues it came from and `payload.run_id`/`payload.task_id` identifies the affected run.

**If depth is non-zero:** the safe default is to treat it as a bug report, not to blindly requeue.
Read the message body, find the run/task it names, check why the corresponding
`talos-orchestrator` log line failed 3 times (search its logs for the `event_id`), fix the root
cause, then decide whether to manually requeue (management UI's "Requeue" button) or just
document/delete it if the underlying run is already stale.
