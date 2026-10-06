---
id: KILO-7
title: 'JetBrains plugin: provider selection alongside model selection'
status: In Progress
assignee: []
created_date: '2026-10-06 07:27'
updated_date: '2026-10-06 09:13'
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
- **Fork context**: this repo is the **Deluxe edition** — a fork of the **Kilo Code repo** (`upstream = Kilo-Org/kilocode`), NOT of opencode. `packages/kilo-jetbrains/` is upstream-synced code — changes there carry real upstream-conflict surface. **Strategy (user rule, codified in AGENTS.md + GLOSSARY.md): prefer Deluxe-owned implementations (this fork's own components/dialogs/windows); stay away from Kilo Code UI — keep edits inside upstream UI files as small as possible, build new surfaces in fork-owned files.**
- **Settled intent (KILO-7.2)**: in-picker route choice for multi-server providers (OpenRouter sub-providers like parasail, digitalocean for glm-5.3-flash); live route list proxied by the CLI (new endpoint + DTO); model+route+effort freeze as one lock at a session's first message, explicit unfreeze, changes apply next message onward; sticky per session. kilo.json `options.provider.order` remains a working config-only interim.
- **Verified mechanics (CLI side)**: request options pipeline exists end-to-end — `request.ts:112` merges `model.options` into a flat options bag → `ProviderTransform.providerOptions` (`transform.ts:1723`) namespaces per provider → AI SDK. `kiloProviderOptions` (`kilocode/provider-options.ts`) types OpenRouter options via `OpenRouterProviderOptions` (which includes OpenRouter's `provider: { order, only, … }` routing preferences).
- **Verified mechanics (JetBrains side)**: `ModelOptionsDto` is `description`-only (`ProviderDto.kt:68-70`) — request options never surface in the picker; rows are `(providerID, modelID)`; variants system exists (ReasoningPicker).
- Work sessions: load `jetbrains-ui` / `jetbrains-session` / `jetbrains-arch` skills as needed; domain vocabulary in `GLOSSARY.md`, ADRs in `docs/adr/` when decisions deserve one.
- Tickets are decisions only — resolving a ticket closes it; implementation planning happens after the map is clear.

## Decisions so far

- [Map the existing provider/model selection surface in the JetBrains plugin](KILO-7.1): CLI already ships full catalogs for non-connected providers; the kilo+connected filter is client-side only; `PickerPopup` is single-list with no nav stack; only persisted selection is per-agent `model.json`, written solely by the New Worktree dialog. Findings: `.kilo/plans/wayfinder/kilo-7.1-selection-surface.md`. Flag confirmed: `ProvidersDto.defaults` read agent-keyed (`session/controller/SessionSelection.kt:38-41`) while wire is provider-keyed — dead fallback branch.
- [Settle what "choosing a provider" means in the JetBrains model picker](KILO-7.2): in-picker route choice with a live CLI-proxied route list; model+route+effort freeze as one lock at a session's first message, explicit unfreeze, changes apply next message onward; sticky per session. Catalog-provider disambiguation and the config-only mechanism excluded (kilo.json path noted as interim).

## Not yet specified

- Whether the decided pattern should carry over to the CLI TUI and VS Code Agent Manager (nice-to-have per user).
- Whether other multi-server providers (beyond OpenRouter) need the same affordance — partially depends on the route-list shapes surveyed in KILO-7.5.

## Out of scope
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Map has Destination, Notes, Decisions so far, Not yet specified, Out of scope sections
- [ ] #2 All child decision tickets created with wayfinder:* labels and blocking edges wired
- [ ] #3 Each resolved ticket lands a resolution comment + a Decisions-so-far pointer on this map
<!-- AC:END -->
