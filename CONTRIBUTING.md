# Contributing to Talos

Talos welcomes focused contributions that follow the repository's implementation plan, service
boundaries, and licensing rules. Read `AGENTS.md`, `LICENSING.md`, and `CLA.md` before starting.

## Contribution workflow

1. Open or select an issue that describes one bounded change. For architecture or contract changes,
   confirm the proposal against `docs/Talos_Implementation_Plan.pdf` before implementation.
2. Create a short-lived branch from the current `main`. Use a descriptive name such as
   `feature/task-123-short-description` or `fix/task-123-short-description`.
3. Keep the change scoped. Do not mix application behavior, generated output, broad reformatting,
   and unrelated refactoring in one pull request.
4. Add or update tests with the implementation. Update contracts and documentation when behavior or
   public interfaces change.
5. Run the relevant project tests and `./scripts/check-licenses.sh`. Run the naming guard documented
   in `AGENTS.md` before requesting review.
6. Open a pull request to `main` with the problem, approach, verification commands and results,
   operational impact, and any remaining risks or follow-ups.

Do not commit directly to `main`. Pull requests should be reviewable, have a clear title, avoid
unrelated generated or lockfile churn, and resolve review feedback with additional commits or a
maintainer-approved update. Maintainers may request that an oversized change be split.

## Testing and documentation

Run the narrowest relevant test suite during development and the complete affected service suite
before review. Changes crossing service or contract boundaries require tests on each affected side.
Do not weaken approval, isolation, secret-masking, or source-of-truth contract checks.

Update user, operator, API, architecture, and phase documentation in the same pull request when the
change affects those surfaces. Append the required entry to `docs/initial_implementation_log.md`;
do not rewrite its history. Generated files must be regenerated from their source rather than edited
as if they were first-party handwritten code.

## Security reports

Do not open a public issue or pull request for a suspected vulnerability. Follow `SECURITY.md` and
report it privately to the placeholder address listed there.

## Contributor Licence Agreement

Vulkan Technologies can accept a contribution only after the contributor agrees to `CLA.md`. Until
an automated CLA system exists, include the following statement in the pull-request description:

> I have read and agree to the Talos Contributor Licence Agreement in `CLA.md`, and I have the right
> to submit this contribution.

A contributor submitting on behalf of an organization must be authorized to bind that organization
or arrange for an authorized corporate representative to agree. Maintainers must record acceptance
before merging.

Contributors retain copyright in their work. The CLA grants Vulkan Technologies the rights needed
to distribute accepted contributions in both open-source Talos releases and commercially licensed
versions. It is a licence grant, not an assignment of ownership.
