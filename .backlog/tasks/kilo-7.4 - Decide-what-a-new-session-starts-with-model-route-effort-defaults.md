---
id: KILO-7.4
title: Decide what a new session starts with (model/route/effort defaults)
status: Done
assignee:
  - dev1
created_date: '2026-10-06 09:13'
updated_date: '2026-10-06 13:48'
labels:
  - 'wayfinder:grilling'
dependencies:
  - KILO-7.3
parent_task_id: KILO-7
priority: medium
type: enhancement
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

Once the shape decision (KILO-7.3) settles the selection unit: what does a **new** session start with — model, route, and reasoning effort?

- Inheritance source: last-used (`model.json` recent), per-agent defaults, or a single global default. KILO-7.1 found the only persisted selection today is per-agent `model.json` written solely by the New Worktree dialog, and `ProvidersDto.defaults` is a dead fallback branch (provider-keyed on the wire, read agent-keyed in `session/controller/SessionSelection.kt:38-41`) — verify/fix or build on it.
- Whether route and effort participate in defaults or always start unset (Auto / provider default).
- Interplay with the freeze: a new session starts unlocked; is the inherited value shown as editable, and does the freeze lock the inherited value at first message?

HITL grilling session with the user; the decision lands as a resolution comment naming the inheritance rule and its contract implications (`ModelStateDto`, `model.json` schema).
<!-- SECTION:DESCRIPTION:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-06 12:27
---
Resolved. The decision (HITL grilling, 2026-10-06):

1. **Model chain — keep 5 tiers, new project tier on top.** A new session resolves: (0) last pick in the project store keyed by agent → (1) per-agent `model.json` override → (2) per-agent config default → (3) global config default → (4) recent → (5) `kilo/kilo-auto/free`. Tier 1 becomes read-only legacy on JetBrains (see #2); tier 4 stays as-is.
2. **Persistence — new project-scoped parameter store.** A project-level `PersistentStateComponent` (workspace.xml storage, `KiloLogSettingsService` precedent) in a Deluxe-owned file is the single JetBrains write target: model pick per (agent), reasoning effort per (provider, model), route per (provider, model), and the remembered **mode/agent** — which moves out of IDE-global `KiloPluginSettings` so nothing cross-populates between IntelliJ projects (user requirement; per-worktree NOT in play — Agent Manager worktree sessions belong to the project). ALL JetBrains writes go there (in-session picks, New Worktree dialog, reasoning/route picks, mode switch); JetBrains stops writing `model.json` (read-only legacy; TUI keeps its own writes). **No `ModelStateDto`/`model.json`/CLI change — KILO-7.3's no-schema-change boundary holds.**
3. **Dead `ProvidersDto.defaults` branch — deleted.** The pre-READY read at `SessionSelection.kt:36-41` goes; bootstrap degrades to the `first` picker item. Verified: the wire is providerID → bare model ID (`defaultModelIDs`, provider.ts:1189-1191) which `modelSelection()` cannot parse — the branch was worse than dead (garbage pairs rejected by `validModelSelection`).
4. **Route — inherit last-used per (project, provider/model); no auto-fallback.** A stale/unavailable inherited route is NOT auto-cleared: the pin stays active until the user explicitly re-picks (unfreeze required when locked); a notice plus the refreshed route list makes the recovery path visible. Accepted consequence: a dead pin (`allow_fallbacks: false`) fails requests hard until re-picked — nothing changes without an explicit user action.
5. **Effort — inherit, Auto when never picked.** Last-used effort inherited per (project, provider/model); when never picked → Auto (null variant = provider default). The forced `firstOrNull()` tier (`SessionController.kt:2445`) is dropped; the machine-global `model.json` variant map stays a lower read tier.
6. **Lock interplay — no badge + stability contract.** A new session starts unlocked; inherited values are ordinary editable picker values. Lock engages at first message. Stability contract: a session's parameters re-resolve ONLY on explicit user actions (pick, agent switch, unfreeze+edit); background events (config patch from Models settings, catalog reload, defaults change) never mutate an open session's values — today's broad `syncModelSelection` re-fires (SessionController.kt:866/945/1194/1204/1262/2502/2523) get narrowed; new sessions resolve fresh (opening one is user-driven).

ADR: `docs/adr/0002-per-project-parameter-store.md`. Glossary: "Project store" added.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Decided what a new session starts with: a new project-scoped parameter store (Deluxe-owned project PersistentStateComponent, workspace.xml) seeds model/route/effort/mode per IntelliJ project — model via the existing 5-tier chain with the project tier on top, effort inherited or Auto when never picked (forced first tier dropped), route inherited last-used and kept even when stale (notice + refreshed list; re-pick on demand). JetBrains stops writing model.json (read-only legacy tier); the dead ProvidersDto.defaults pre-READY branch is deleted; sessions re-resolve only on explicit user actions. No ModelStateDto/model.json/CLI change. ADR-0002 and glossary "Project store" recorded.
<!-- SECTION:FINAL_SUMMARY:END -->
