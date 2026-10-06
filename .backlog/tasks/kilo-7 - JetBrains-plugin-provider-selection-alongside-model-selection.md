---
id: KILO-7
title: 'JetBrains plugin: provider selection alongside model selection'
status: In Progress
assignee: []
created_date: '2026-10-06 07:27'
updated_date: '2026-10-06 07:47'
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

A **decided design** (not an implementation) for choosing *which server routes a given model* in the Kilo **JetBrains plugin**: the UX intent settled, the mechanism/shape chosen, and the required data-contract surface (RPC/DTO/CLI) named — enough to hand off to implementation planning.

## Notes

- Scope: JetBrains plugin (`packages/kilo-jetbrains/`) first; other surfaces (CLI TUI, VS Code Agent Manager) are nice-to-have and ride as fog.
- **Fork context**: this repo is a fork of the **kilo-code repo** (`upstream = Kilo-Org/kilocode`), NOT opencode. `packages/kilo-jetbrains/` is upstream-synced code — changes there carry real upstream-conflict surface. Minimize and isolate changes; `kilocode_change` markers where applicable.
- **Verified intent (user)**: OpenRouter key added as provider; models like glm-5.3-flash are *served by multiple servers inside OpenRouter* (parasail, digitalocean, …) and the user cannot choose which one a request routes to. This is **sub-provider routing inside a provider**, not catalog-provider disambiguation.
- **Verified mechanics (CLI side)**: request options pipeline exists end-to-end — `request.ts:112` merges `model.options` into a flat options bag → `ProviderTransform.providerOptions` (`transform.ts:1723`) namespaces per provider → AI SDK. `kiloProviderOptions` (`kilocode/provider-options.ts`) types OpenRouter options via `OpenRouterProviderOptions` (which includes OpenRouter's `provider: { order, only, … }` routing preferences). So a custom model entry with `options.provider.order` in kilo.json can pin a server **today, config-only, zero code** — runtime verification still owed.
- **Verified mechanics (JetBrains side)**: `ModelOptionsDto` is `description`-only (`ProviderDto.kt:68-70`) — request options never surface in the picker; rows are `(providerID, modelID)`; variants system exists (ReasoningPicker).
- Work sessions: load `jetbrains-ui` / `jetbrains-session` / `jetbrains-arch` skills as needed; domain vocabulary in `GLOSSARY.md`, ADRs in `docs/adr/` when decisions deserve one.
- Tickets are decisions only — resolving a ticket closes it; implementation planning happens after the map is clear.

## Decisions so far

- [Map the existing provider/model selection surface in the JetBrains plugin](KILO-7.1): CLI already ships full catalogs for non-connected providers; the kilo+connected filter is client-side only; `PickerPopup` is single-list with no nav stack; only persisted selection is per-agent `model.json`, written solely by the New Worktree dialog. Findings: `.kilo/plans/wayfinder/kilo-7.1-selection-surface.md`. Flag confirmed: `ProvidersDto.defaults` read agent-keyed (`session/controller/SessionSelection.kt:38-41`) while wire is provider-keyed — dead fallback branch.

## Not yet specified

- Whether the decided pattern should carry over to the CLI TUI and VS Code Agent Manager (nice-to-have per user).
- Whether routes should persist per model/agent (defaults) or be per-session picks.
- Whether other multi-server providers (beyond OpenRouter) need the same affordance.

## Out of scope
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Map has Destination, Notes, Decisions so far, Not yet specified, Out of scope sections
- [ ] #2 All child decision tickets created with wayfinder:* labels and blocking edges wired
- [ ] #3 Each resolved ticket lands a resolution comment + a Decisions-so-far pointer on this map
<!-- AC:END -->
