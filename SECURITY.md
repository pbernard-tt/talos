# Security policy

## Supported versions

Security fixes are developed for the current `main` branch and the latest supported release. Until
Talos publishes its first stable release, only the current `main` branch is supported. After stable
releases begin, the support table should be updated for every release line.

| Version | Supported |
|---|---|
| Current `main` / latest release | Yes |
| Older releases and historical commits | No, unless Vulkan Technologies states otherwise |

## Reporting a vulnerability

Report suspected vulnerabilities privately to `security@example.com`.

**This is a placeholder address. It must be replaced with a monitored Vulkan Technologies security
mailbox before public release.** Do not send production secrets, credentials, personal data, or
unredacted customer data. If sensitive supporting material is necessary, first ask for an approved
encrypted transfer method.

Please do not open a public issue, discussion, or pull request, and do not disclose the vulnerability
publicly before maintainers have had a reasonable opportunity to investigate and remediate it.

Include as much of the following as possible:

- affected version, commit, deployment mode, and component;
- vulnerability type and likely security impact;
- prerequisites and a minimal, reproducible sequence of steps;
- proof-of-concept material with destructive actions and secrets removed;
- relevant logs, stack traces, requests, responses, or configuration, suitably redacted;
- known mitigations or workarounds;
- whether the issue is known to or exploited by others; and
- your preferred contact details, attribution, and disclosure timeline.

## What to expect

Vulkan Technologies aims to acknowledge a complete report within three business days and provide an
initial status within seven business days. These are targets, not contractual service levels. We may
ask for clarification, coordinate a fix and release, request validation, and agree on a disclosure
date based on severity and user risk.

Please act in good faith, avoid privacy violations and service disruption, test only systems you own
or are authorized to test, minimize data access, and securely delete data obtained during research.
Allow a reasonable remediation period before disclosure. Vulkan Technologies will seek to credit
reporters who request attribution and comply with this policy, subject to legal and safety limits.

This policy does not authorize activity prohibited by law and is not a bug-bounty promise, safe-harbor
contract, or waiver of rights. It should be reviewed by qualified counsel before public launch.
