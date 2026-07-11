# Phase 16 report — MinIO artifact storage

Phase 16 is complete. Run artifacts (transcripts, patches, test reports, generated docs --
Section 4.2) now flow through an `ArtifactStore` interface with `LocalVolumeArtifactStore`
(default) and `MinioArtifactStore` implementations, finally implementing the
`POST /internal/v1/runs/{id}/artifacts` contract line Section 10.4 had reserved since the plan's
first draft but no phase had built.

## What works

- **`ArtifactStore` interface (`dev.talos.artifacts`).** `write`/`read`/`delete` keyed by a logical
  storage key (`{runId}/{kind}/{name}`), mirroring how `DeployProvider` sits behind `DeployService`
  (Phase 10). `LocalVolumeArtifactStore` resolves keys under a single root directory and rejects any
  key that would escape it (path-traversal guard); `MinioArtifactStore` wraps the official
  `io.minio:minio` SDK, ensures its bucket exists on construction, and never logs or includes its
  access/secret key in any exception message. Bean selection is `talos.artifact-store-type`
  (`local`, default, or `minio`) via `@ConditionalOnProperty` -- exactly one bean is ever registered.
  `talos-api` is the only caller of `ArtifactStore` (`ArtifactService`), the same "only writer"
  principle Hard constraint 5 already applies to PostgreSQL; worker containers and both Python
  services never receive object-store credentials.
- **Metadata table.** New `run_artifacts` (migration V008): id, run_id, kind (`DIFF_PATCH` |
  `TRANSCRIPT` | `TEST_REPORT` | `GENERATED_DOC`), name, storage_key, content_type, size_bytes,
  created_at. `ArtifactService` validates uploaded names against `[A-Za-z0-9._-]+` before ever
  building a storage key, so a hostile `name` can't reach `LocalVolumeArtifactStore`'s own
  traversal guard in the first place.
- **Upload/list/download/delete endpoints.**
  `POST /internal/v1/runs/{id}/artifacts` (multipart `{kind, name, file}`, service-token-authenticated)
  is called directly by the runner supervisor -- not relayed through the orchestrator. This is not a
  new communication path: Hard constraint/Section 10.1's `/internal/v1` namespace already lists
  `talos-runner-supervisor` as a caller alongside the orchestrator, and Appendix A's
  `TALOS_INTERNAL_API_TOKEN` row for the runner supervisor was already annotated "Auth for
  artifact/log posts" back when it was first introduced -- this phase is the first to actually
  exercise that reserved path. `GET /api/v1/runs/{id}/artifacts` lists; `GET
  /api/v1/runs/{id}/artifacts/{artifactId}/download` streams bytes with `Content-Disposition:
  attachment` and the artifact's real content type -- byte-identical regardless of which store is
  configured (verified live against both). `DELETE /internal/v1/runs/{id}/artifacts` removes every
  stored artifact plus its metadata rows for a run, called by the orchestrator's retention sweep.
- **Runner supervisor now produces and posts three real artifact classes.** `diff.patch` already
  existed (`diff_capture.py`) and is now posted after every `/runs/{id}/diff` call. The agent
  transcript already existed on disk (`AgentResult.raw_output_path`, since Phase 7) but was
  previously dropped between the runner supervisor and the API entirely -- `execute.py` now posts it
  once the adapter's result is available. Test output previously only streamed as ndjson log lines
  with nothing written to disk; `test_command.py` now tees every line into
  `{workspace}/../artifacts/test-report.log` alongside `diff.patch` and posts that file too. All
  three posts are best-effort (`artifact_client.py`: caught, logged, never raised) since the
  artifact's bytes still exist on the runner supervisor's own filesystem regardless of whether the
  API-side post succeeds, and a storage hiccup must not fail the run step that produced the artifact.
- **Retention extended (Phase 11).** `apps/orchestrator`'s `retention.py` now calls the new
  `DELETE /internal/v1/runs/{id}/artifacts` for every run id the runner supervisor's workspace
  cleanup actually reported as deleted (not merely every candidate -- a run left alone because it's
  younger than `max_age_days` keeps its artifacts too).
- **One-shot migration.** `ArtifactMigrationRunner` (`talos.migrate-artifacts-on-boot=true`, an
  `ApplicationRunner` gated by `@ConditionalOnProperty`, mirroring `IntegrationServiceAccountSeeder`'s
  existing idempotent-seeder shape) copies every already-recorded artifact from the local volume into
  whichever store is now configured, on the next boot after an operator flips
  `TALOS_ARTIFACT_STORE_TYPE` to `minio`. A per-artifact failure is logged and skipped rather than
  aborting the whole migration.
- **Contracts.** `packages/contracts/openapi.yaml` is at `0.16.0`: `ArtifactKind`/`RunArtifact`
  schemas, the four new paths, 404/422 responses documented.
- **Frontend.** Run detail page gained an "Artifacts" section (name, kind, size, a download button
  that fetches the blob through the authenticated Angular client and triggers a browser download --
  not a raw `<a href>`, since the download endpoint requires the JWT bearer header).
- **Infra.** `infra/docker-compose.dev.yml` gained a `talos-minio` service (Section 18's reserved
  row, brought up unconditionally in dev since it costs nothing idle) and a `talos_artifacts` named
  volume for `talos-api`; `talos-api`'s `TALOS_ARTIFACT_STORE_TYPE` is left unset by default so
  bringing `talos-minio` up changes no default behavior. `apps/api/Dockerfile` now pre-creates and
  chowns `/var/talos/artifacts` to the non-root `talos` user -- found live (see below) that this was
  missing and the local-store default would 502 on every write without it.

## Found live, not by unit tests

The first real run through the rebuilt dev `docker compose` stack (`scripts/smoke.sh`) reached
`WAITING_APPROVAL` correctly, but `GET /api/v1/runs/{id}/artifacts` came back empty and the runner
supervisor's logs showed `502` responses from every artifact post. `docker exec talos-api id` showed
the container runs as non-root `talos` (uid 999); `/var/talos` didn't exist at all and the user had
no permission to create it. `apps/api/Dockerfile`'s `useradd` step never provisioned `/var/talos` --
nothing in Phases 1-15 ever needed a writable path outside the JVM's own working directory, so the
gap was invisible until this phase's default artifact root actually got written to. Fixed by having
the Dockerfile `mkdir -p /var/talos/artifacts && chown -R talos:talos /var/talos` before `USER talos`,
and adding a named `talos_artifacts` volume mount in the dev compose file so the directory (and its
ownership, which Docker copies from the image into a fresh named volume on first mount) persists
across container recreates. Re-verified after the fix: `scripts/smoke.sh` passed, and
`GET /api/v1/runs/{id}/artifacts` showed both the `diff.patch` and `transcript.txt` rows with
correct sizes.

Separately (not this phase's bug, but hit while smoke-testing): the first `scripts/smoke.sh` run
failed with `docker: permission denied ... /var/run/docker.sock` inside `talos-runner-supervisor`
because the container's `group_add` GID (default `999`) didn't match this host's actual `docker`
group GID (`989`). This is the pre-existing `TALOS_DOCKER_GID` env var CLAUDE.md already documents a
workaround for; recreating the container with `TALOS_DOCKER_GID=989` set resolved it. Left
undocumented as a Phase 16 change since it's host-environment configuration, not a code or image
defect.

## Documented deviations

1. **The runner supervisor posts artifacts directly to `talos-api`, not relayed through the
   orchestrator.** The plan's Phase 16 task list says "runner/orchestrator continue to hand
   artifacts to the API -- no new communication path." Read literally this could mean "route
   everything through the orchestrator like `/changes` already does." This implementation instead
   has the runner supervisor call `/internal/v1/runs/{id}/artifacts` itself, using its own
   `TALOS_INTERNAL_API_TOKEN` -- justified because Section 10.1 already lists the runner supervisor
   as an allowed `/internal/v1` caller and Appendix A's token row for it was already annotated for
   "artifact/log posts" since the row was first written, well before this phase existed. Routing
   through the orchestrator instead would have meant inventing a *new* runner-supervisor ->
   orchestrator HTTP endpoint to relay raw bytes -- a genuinely new path -- to avoid using a path the
   plan had already reserved. This reading was chosen after re-reading `apps/runner-supervisor/src/
   talos_runner_supervisor/config.py`'s own comment on `internal_api_token`, written in Phase 6,
   which already anticipated exactly this.
2. **"Generated docs" has no producer yet.** `ArtifactKind.GENERATED_DOC` exists in the schema, the
   database, and the store/service layer -- the upload/list/download/delete machinery is fully
   generic across all four kinds -- but nothing in the pipeline currently emits one. The plan lists
   "generated docs" as an artifact class to move to object storage, not as a feature to invent from
   scratch in this phase; a future phase that starts producing them (e.g. an agent-authored design
   doc) needs no further storage-layer change.
3. **Migration is a boot-time flag, not a standalone CLI command.** The plan says "a one-shot
   migration command." This repo has no precedent for a standalone Java CLI entrypoint separate from
   the Spring Boot application itself; `ArtifactMigrationRunner` reuses the existing
   `ApplicationRunner` + `@ConditionalOnProperty` shape `IntegrationServiceAccountSeeder` already
   established for "runs once at boot, idempotent-ish, driven by a `talos.*` property." Building a
   second entrypoint (a `main()` class, a Gradle `application` task, packaging concerns) for a copy
   that only ever needs to run once per install was judged disproportionate to this phase's scope.

## Stubbed / deferred

- No presigned-URL downloads (explicitly out of scope for "the first cut" per the plan) -- every
  download goes through `talos-api`, which is also why worker containers and the Python services
  never need object-store credentials at all.
- `GENERATED_DOC` artifacts (see deviation 2) -- no producer yet.
- MinIO is not deployed in the Dokploy production compose (Section 18 still marks it "optional,
  later"); this phase only wires it into the dev compose and the Java-side store implementation.

## Verification

- `apps/api`: full suite -- `sg docker -c 'GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon
  test'` -- BUILD SUCCESSFUL, 175 tests, 0 failures, 0 errors, zero regressions. New coverage:
  `ArtifactStoreContractTest` (abstract, run against both `LocalVolumeArtifactStoreContractTest`
  and `MinioArtifactStoreContractTest` via a real MinIO Testcontainers `GenericContainer` --
  write/read round-trip, overwrite-not-append, delete, delete-of-missing-key is a no-op, plus a
  local-only path-traversal-rejection test), `RunArtifactControllerIntegrationTest` (upload -> list
  -> byte-identical download; missing service token -> 401; unsafe artifact name -> 422; unknown
  artifact id -> 404; an artifact belonging to a different run -> 404 on that run's URL;
  delete-all -> empty list and 404 on subsequent download), `ArtifactMigrationRunnerTest` (copies a
  seeded local file into a stand-in destination store; skips entirely and touches the repository not
  at all when `artifact-store-type` isn't `minio`; one artifact's missing source file doesn't abort
  copying the rest). `sg docker -c ...` was required -- this sandbox's default shell group
  membership doesn't include `docker` in-session (same wrapper CLAUDE.md already documents).
- `apps/orchestrator`: `uv run pytest` -- 33 passed. New/changed: `test_retention.py` asserts
  `delete_artifacts` is called once per run id `runner_client.cleanup` actually reports deleted (not
  once per candidate), and not at all when nothing was deleted.
- `apps/runner-supervisor`: `uv run pytest` -- 31 passed (24 existing + 7 new). New:
  `test_artifact_client.py` (skips when no token; skips when the file doesn't exist; posts the
  correct multipart body/params/header on success; logs and swallows HTTP errors rather than
  raising), `test_test_command.py` (null command writes no report file; command output is captured
  to `test-report.log` in both content and per-line log events; a failing command still writes its
  output and reports the real exit code).
- `apps/web`: `npm run generate:api` regenerated the client (new `RunArtifact`/`ArtifactKind`
  models, `listRunArtifacts`/`downloadRunArtifact` on `RunsService`) under Node 22
  (`nvm use 22` first, per this repo's `.nvmrc` requirement). `npm run build` and `npx ng test
  --watch=false` both succeeded (14 existing tests, no regressions; no new frontend spec files,
  consistent with this repo's existing frontend test depth). Generated-file trailing-whitespace
  stripped before `git diff --check` (the same recurring generator quirk noted in the Phase 13/14/15
  reports).
- `packages/contracts/openapi.yaml`: parsed with Python/YAML -- version `0.16.0`; all four new paths
  and both new schemas present.
- Live `docker compose` stack: rebuilt `talos-api`/`talos-runner-supervisor`/`talos-orchestrator`
  images against this repo's own long-running dev stack (weeks-old data, reused across Phases
  12-15's verification too); confirmed `V008 - run artifacts` applied cleanly. Ran
  `scripts/smoke.sh` end to end -- `WAITING_APPROVAL` reached, then confirmed via `GET
  /api/v1/runs/{id}/artifacts` that both `diff.patch` and `transcript.txt` were recorded with
  correct sizes, and downloaded `diff.patch` back via `GET .../download` -- byte-identical to the
  unified diff git itself produced. Separately started a second `talos-api` container
  (`TALOS_ARTIFACT_STORE_TYPE=minio`, same Postgres) against the same run, uploaded an artifact
  through it, confirmed via `mc ls` inside `talos-minio` that the object landed in the real bucket,
  downloaded it back byte-identical through that instance, and confirmed the original
  `local`-configured instance correctly *cannot* read it (`ARTIFACT_STORE_READ_FAILED`) -- proving
  real storage separation, not a shared cache. Grepped that container's full log output for both
  MinIO credential strings -- zero matches. Exercised `DELETE /internal/v1/runs/{id}/artifacts`
  against the live stack -- `204`, followed by an empty list and `404` on the previously-valid
  download URL.
- Source-scoped naming guard returned no matches.
- Not checked: the one-shot migration runner against a real multi-hundred-artifact install (unit/
  integration-tested only, not load-tested); MinIO wired into the Dokploy production compose
  (deferred per Section 18, "optional, later"); presigned-URL downloads (explicitly out of scope for
  this phase).
