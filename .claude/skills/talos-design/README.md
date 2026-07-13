# Talos Design System

Design system for **Talos** — a self-hosted, agent-agnostic development control plane that
orchestrates coding agents (Claude Code, OpenAI Codex CLI, OpenCode, OpenHands, Custom Shell)
across a portfolio of projects: task planning, live agent runs, governed review/approval,
gated deployments, memory, cost tracking, and audit.

## Sources

No Figma file or existing codebase was provided for Talos. This system was built from:

- `Talos_Implementation_Plan.pdf` (uploaded) — the full product/engineering spec: data
  model, state machines, approval rules, security boundaries, and the intended Angular 22
  dashboard scope (Section 15). Every workflow, status enum, and governance rule in this
  design system traces back to that document.
- `Talos.dc.html` (this project's root) — the original interactive prototype built directly
  against the plan. This design system was **extracted from that prototype**: its inline
  color values, spacing, radii, and screen layouts are the ground truth for every token and
  component here. If the prototype and this design system ever disagree, treat the
  prototype as the earlier draft and this system as the corrected, reusable version.
- The uploaded Talos logo mark (`assets/talos-logo.png`) — the only supplied brand asset.

No first-party UI kit, icon set, or font files were supplied; substitutions are flagged in
their respective sections below.

## Index

| Path | What's in it |
|---|---|
| `styles.css` | Root stylesheet — `@import`s every token file. Link this one file to use the system. |
| `tokens/` | Colors, typography, spacing, radii, shadows, motion — CSS custom properties |
| `components/badges/` | StatusBadge, AgentBadge, AuthModeBadge, RiskBadge |
| `components/forms/` | Button, Input, Select |
| `components/navigation/` | Tabs |
| `components/overlays/` | Dialog, Toast |
| `components/data-display/` | MetricCard, StatusTimeline |
| `components/board/` | TaskCard (Kanban) |
| `components/runs/` | RunRow, DiffBlock |
| `guidelines/` | Foundation specimen cards (Colors, Type, Spacing, Elevation, Motion, Brand) |
| `ui_kits/talos/` | Full click-through recreation of all 16 product screens |
| `assets/` | Logo mark  |
| `icons/` | Generated favicon/app-icon set |
| `Talos.dc.html` (project root) | The original interactive prototype this system was extracted from |
| `talos-data.js` (project root) | The prototype's mock-data boundary (see its own file header) |

### Intentional additions

The plan names screens and data, not a component inventory, so the primitives in
`components/` (Button, Input, Select, Tabs, Dialog, Toast, MetricCard) are a standard set
sized to what the 16 screens actually use — not invented beyond that. `StatusTimeline`,
`TaskCard`, `RunRow`, and `DiffBlock` are named directly after concepts the plan defines
(run state machine, Kanban card, run list row, unified diff) and only encapsulate what's
already specified there.

## Content fundamentals

**Voice:** precise, calm, operator-to-operator. Talos talks like the senior engineer running
the on-call rotation, not a marketing site. Short, declarative sentences. States facts about
system status; never hypes a feature.

- **Plain language over jargon** in the UI (buttons say "Approve & push", not "Execute
  merge operation"), but exact technical vocabulary in data (`WAITING_APPROVAL`,
  `RUNNING_AGENT`) is shown verbatim — never softened or renamed, because reviewers need to
  recognize the real state-machine value.
- **Consequences are always spelled out before a destructive or governance action**:
  "Talos will commit, push the branch, and open a pull request. This cannot be undone." —
  never a bare "Are you sure?".
- **Advisory language is explicit**: recommendations are always labeled "Advisory only —
  Talos never auto-selects an agent or executes these for you."
- **No filler, no emoji.** This is an operator console; copy is dense and specific
  ("14 tests failed after dependency bump — incompatible Jackson serialization defaults"),
  never vague ("Something went wrong").
- **Numbers are real.** Every count, cost, and duration shown is computed from the
  underlying data — a subscription-auth run's dollar cost is shown as "Not reported", never
  estimated.
- **Casing:** sentence case for buttons/labels/headings; ALL CAPS with tracking reserved for
  small eyebrow labels and table column headers only.

## Visual foundations

**Direction:** dark-first enterprise operator console — deep graphite/blue-black, one
restrained purple accent, dense but readable. Deliberately not a marketing site, not a
generic admin template, not neon/cyberpunk.

- **Backgrounds:** `--surface-app` (#080A0F) as the page base; cards sit on `--surface-card`
  (#11151E) with a 1px border, never a shadow at rest. Elevated surfaces (dropdowns, the
  notification panel) step up to `--surface-elevated` (#161B26) with `--shadow-2`. No
  patterns, no grain, no photography — the only imagery is the logo mark.
- **Purple usage:** `--accent` (#8B5CF6) is reserved for selected nav, primary buttons, the
  agent-pulse signal, progress fills, and focus rings. It never fills a full card or page
  background — surfaces stay graphite so purple keeps its signal value.
- **Type:** Inter for all UI text, JetBrains Mono for anything identifying or literal — run
  IDs, branch names, commands, diffs, timestamps in logs. This pairing is a **substitution**:
  no first-party fonts were supplied; Inter + JetBrains Mono were chosen to match the
  "precise, technical, calm" brief. Flag and swap if the team has real fonts.
- **Spacing:** 4px base grid; operational surfaces (board, tables) run dense (8–16px), while
  dialogs and empty states get more air (16–24px).
- **Radii:** moderate throughout — 8–12px for cards/buttons, pill only for status badges and
  tags. No pill-shaped buttons, no fully-rounded cards.
- **Shadows:** cool-tinted (`rgba(11,16,32,…)`), never pure black, and only on elevated
  overlays (dialogs, dropdowns, the command palette). Resting cards use a border only.
- **Motion:** quiet and purposeful. One entrance fade (`talos-fade-in`, 420ms) and one
  signature loop — a soft purple "agent pulse" ring beside anything actively executing
  (sidebar badge, board card, Command Center orchestration row, Run Detail header). Nothing
  else animates continuously. All animation respects `prefers-reduced-motion`.
- **Hover/press:** buttons step one shade darker/lighter on hover, never scale; the one
  exception is the agent-pulse ring, which is intentionally always-on for live state, not a
  hover effect.
- **Data density:** this is an operator console for long sessions — tables and board cards
  default to compact information density; a "Compact / Comfortable" toggle exists on the
  Task Board specifically because card density is the one place operators want to choose.

## Iconography

No icon set was supplied. The prototype (`Talos.dc.html`) uses hand-drawn inline SVGs in a
**Lucide-compatible style** (24×24, 2px stroke, round caps, `currentColor`) — this is a
**flagged substitution**. For this design system going forward, link
[Lucide](https://lucide.dev/) from CDN rather than hand-drawing further icons:

```html
<script src="https://unpkg.com/lucide@latest"></script>
```

Guidance: 16px inline with body text, 20px default UI, 24px for nav/feature glyphs. Icons
inherit `currentColor` in the large majority of cases; use `--accent`/`--accent-soft` only
when the icon itself is the focal element (an empty-state glyph, a feature callout). Never
mix icon families on one screen, never substitute emoji for a UI icon. The only real asset
supplied is the logo mark (`assets/talos-logo.png`) — an angular purple→cyan "T"/mask glyph
on near-black, used as the sidebar/login mark and the source for the generated favicon set
(`icons/` at the project root).

## Iterate with me

**Caveats:**
- Fonts and icons are flagged substitutions (Inter/JetBrains Mono, Lucide) — send real
  brand assets if they exist and I'll swap them in everywhere at once via the token files.
- The `_ds_bundle.js` namespace referenced in every `.card.html` and in
  `ui_kits/talos/index.html` is written as `window.TalosDesignSystem` — if the compiler
  assigns a different generated namespace, tell me and I'll do a one-shot find/replace
  across every card and the UI kit.
- The UI kit simplifies a few things from the full prototype for clarity (Project Detail
  has 4 tabs here vs. 9 in the prototype; Run Detail's live log streaming isn't re-simulated
  here) — say the word and I'll bring any of that fidelity back into the kit.

**Ask:** tell me which screen, component, or token feels off and I'll iterate — this is a
first pass meant to be corrected, not a final artifact.
