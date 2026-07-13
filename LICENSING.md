# Talos licensing

> **Informational summary.** This file explains the intended licensing structure but does not
> replace the applicable licence text or constitute legal advice. The licensing structure and
> contributor agreement should be reviewed by a qualified software-licensing attorney before a
> public production release.

Talos is available under an open-source community licence, with separate licences for selected
reusable packages and documentation. Vulkan Technologies also offers commercial licensing for
uses that require terms different from the applicable open-source licence.

## Licence boundaries

| Material | Licence or policy |
|---|---|
| Core backend, frontend, orchestration engine, agent runtime, management interface, deployment configuration, and operational scripts | `AGPL-3.0-or-later` |
| `packages/agent-adapter-spec/` | `Apache-2.0` |
| `packages/contracts/` | `Apache-2.0` |
| `packages/project-config-schema/` | `Apache-2.0` |
| Generated Angular API client in `apps/web/src/app/api/` | `Apache-2.0` |
| Original documentation in `docs/` | `CC-BY-4.0`, except code samples and executable scripts as described below |
| Talos name, logos, product names, favicon, visual identity, and other branding | Not granted under the software or documentation licences; see `TRADEMARKS.md` |
| Enterprise-only modules, proprietary integrations, hosted-service components, and OEM/white-label materials | Commercial terms, when and if separately provided |

A directory-specific `LICENSE`, an SPDX identifier, or a third-party notice takes precedence for
the material it covers. Executable scripts in `docs/` and code samples copied from the software
retain their applicable software licence. Third-party material remains under its own terms.

No enterprise or proprietary module directory was present when this policy was introduced. A
future proprietary module must be kept behind an explicit licensing boundary and include its own
licence notice; it must not silently inherit the repository's AGPL licence.

## Community Edition

Except for the explicit exceptions above, the Talos Community Edition is licensed under the GNU
Affero General Public License, version 3 or any later version, as published by the Free Software
Foundation (`AGPL-3.0-or-later`). The complete version 3 text is in `LICENSE`.

Subject to the AGPL, you may use Talos for private, public, or commercial purposes; self-host it;
inspect and modify it; and redistribute original or modified copies. The AGPL does not prohibit a
third party from charging for, hosting, supporting, or selling the software. Those activities must
comply with the AGPL's conditions when the AGPL applies.

Among other obligations, distributions must preserve required notices and make Corresponding
Source available as the AGPL requires. If you modify Talos and users interact with that modified
version remotely over a computer network, section 13 requires the modified version to offer those
users an opportunity to receive its Corresponding Source in the manner specified by the AGPL.
Consult the full licence text for the controlling terms.

## Commercial licensing

A separate commercial licence is available for organizations that want to use covered Talos code
under terms other than the AGPL. For example, proprietary embedding, closed-source modifications
made available as a network service, OEM distribution, white-label distribution, or redistribution
under non-AGPL terms requires a commercial licence if the intended use cannot or will not comply
with the AGPL. Commercial licensing may also cover enterprise modules, support, service levels,
and indemnification. See `COMMERCIAL-LICENSE.md`.

Commercial rights are granted only by a separate written agreement signed by Vulkan Technologies.
This repository does not itself grant those commercial rights.

## Previous MIT-licensed revisions

Repository revisions before this licensing structure was adopted included an MIT licence. Rights in
copies already received under that MIT licence are not retroactively revoked by changing the licence
for a later revision. Release managers should identify the first AGPL Community Edition commit and
tag, preserve the historical notices, and confirm that Vulkan Technologies has authority from every
relevant historical rightsholder to publish that revision under AGPL and commercial terms.

## SDKs and reusable packages

Only SDKs and integration libraries explicitly identified in the boundary table and carrying a
directory-level Apache License are licensed under `Apache-2.0`. Their permissive licence is intended
to let clients and integrations communicate with Talos without causing an application that merely
uses the SDK to inherit the core platform's AGPL licence. The Apache License's own conditions still
apply.

## Documentation

Original Talos documentation under `docs/` is licensed under Creative Commons Attribution 4.0
International (`CC-BY-4.0`) where explicitly identified. Attribution and other CC BY conditions
apply. Code samples, configuration excerpts, executable scripts, third-party quotations, and
third-party images do not automatically receive CC BY; they retain their applicable licences.

## Trademarks

None of the AGPL, Apache 2.0, or CC BY 4.0 grants permission to use the Talos trademarks, logos, or
branding as a source identifier. Forking or redistributing the code does not authorize a party to
present its version as an official Talos product or service. See `TRADEMARKS.md`.

## Questions

Licensing enquiries: `licensing@example.com`

This is a placeholder address and must be replaced with a monitored Vulkan Technologies address
before public release.
