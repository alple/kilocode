---
id: KILO-7
title: 'JetBrains plugin: provider selection alongside model selection'
status: In Progress
assignee: []
created_date: '2026-10-06 07:27'
updated_date: '2026-10-06 07:40'
labels:
  - 'wayfinder:map'
dependencies: []
priority: medium
type: epic
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Destination

A **decided design** (not an implementation) for choosing *which provider* serves a given model in the Kilo **JetBrains plugin**: the UX intent settled, the picker shape chosen (extend / duplicate / new), and the required data-contract surface (RPC/DTO/CLI) named — enough to hand off to implementation planning.

## Notes

- Scope: JetBrains plugin (`packages/kilo-jetbrains/`) first; other surfaces (CLI TUI, VS Code Agent Manager) are nice-to-have and ride as fog.
- **Fork context (corrected)**: this repo is a fork of the **kilo-code repo** (`upstream = Kilo-Org/kilocode`), NOT opencode. `packages/kilo-jetbrains/` is upstream-synced code — changes there carry real upstream-conflict surface. Minimize and isolate changes; `kilocode_change` markers where applicable.
- **Verified intent (user, 2026-10-06)**: user added an OpenRouter key as a provider; for a model like glm-5.3-flash (offered by both Kilo Gateway and OpenRouter) they can select the model but cannot choose *which provider/server routes it*. Not interested in Kilo Gateway specifically; the point is same-model-different-provider disambiguation.
- Verified mechanics: picker rows are per `(providerID, modelID)`; `onSelect → selectModel(provider, id) → per-turn PromptDto` — provider-aware routing exists end-to-end *when the row is found*. Rows render model name only; the inline provider label shows only on favorites (`ModelPickerRenderer.kt:93`); the Recommended section does not name its provider. kilo+connected-only filtering is client-side (`ModelItems.kt:15`, `SessionController.kt:1226-1228`).
- Open runtime facts: whether the user's OpenRouter models actually appear in their picker (depends on `connected` state + catalog reach); whether same-model rows appear once or twice.
- Work sessions: load `jetbrains-ui` / `jetbrains-session` / `jetbrains-arch` skills as needed; domain vocabulary in `GLOSSARY.md`, ADRs in `docs/adr/` when decisions deserve one.
- Tickets are decisions only — resolving a ticket closes it; implementation planning happens after the map is clear.

## Decisions so far

- [Map the existing provider/model selection surface in the JetBrains plugin](KILO-7.1): CLI already ships full catalogs for non-connected providers; the kilo+connected filter is client-side only (no CLI change needed to widen the picker); `PickerPopup` is single-list with no nav stack; only persisted selection is per-agent `model.json`, written solely by the New Worktree dialog. Findings: `.kilo/plans/wayfinder/kilo-7.1-selection-surface.md`. Flag confirmed in review: `ProvidersDto.defaults` is provider-keyed on the wire but read agent-keyed in `session/controller/SessionSelection.kt:38-41` — dead fallback branch.

## Not yet specified

- Whether the decided pattern should carry over to the CLI TUI and VS Code Agent Manager (nice-to-have per user).
- Whether selecting a currently-disconnected provider from the picker (with inline connect) is in scope.
- Per-agent / per-worktree default provider+model persistence behavior.

## Out of scope

- Sub-provider routing *inside* a provider (e.g. OpenRouter's internal server routing, Kilo Gateway auto-routing): the user's need is choosing the catalog provider, not the sub-route.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Map has Destination, Notes, Decisions so far, Not yet specified, Out of scope sections
- [ ] #2 All child decision tickets created with wayfinder:* labels and blocking edges wired
- [ ] #3 Each resolved ticket lands a resolution comment + a Decisions-so-far pointer on this map
<!-- AC:END -->
