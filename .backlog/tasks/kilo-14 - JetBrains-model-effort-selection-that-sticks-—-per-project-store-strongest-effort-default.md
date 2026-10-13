---
id: KILO-14
title: >-
  JetBrains: model + effort selection that sticks — per-project store,
  strongest-effort default
status: To Do
assignee:
  - dev1
created_date: '2026-10-13 18:56'
updated_date: '2026-10-13 18:57'
labels: []
dependencies: []
ordinal: 37000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Model and reasoning-effort picks in the JetBrains plugin live in the machine-global `model.json` (shared with the TUI), keyed without any project dimension: changing model/effort in one IntelliJ project changes what other projects show, and the same file is the TUI's global state. On top, a never-picked effort falls back to the FIRST listed variant (`SessionController.kt` `selectResolvedModel` → `model.variants.firstOrNull()`; same forced fallback in the New Worktree dialog) — the lowest effort ("min") shows up as the default and the user must switch to the strongest every time.

A previous attempt (KILO-8.3 per-project parameter store + KILO-8.4 parameter picker) was implemented but survived only on the `archive/deluxe-my-features` branch — the Deluxe line was reset to stable without it, and the user reports it did not work in practice. **This epic re-implements the requirements fresh, deliberately small.** Do not recover that code; use it only as a reference for what was tried.

## Decided (settled with the developer, 2026-10-13)

1. **Fork focus** — the Deluxe edition targets the JetBrains plugin only (AGENTS.md "Fork focus" rule). TUI is nice to have; VS Code compatibility is not a goal. No CLI changes are in scope: the frontend already sends model + variant per prompt.
2. **Per-project parameter store (fresh)** — a project-level `PersistentStateComponent` storing the picks in the IDE project's workspace.xml: model per agent, reasoning effort per (provider, model), remembered agent. Pattern precedent: the app-level `KiloLogSettingsService`; this is the plugin's first project-level store. Deluxe-owned package.
3. **Stickiness contract** — settings stick to the session and are **inherited from the given project**: a session keeps whatever it was seeded/chose; a new session starts from the project's stored picks. Background events (catalog reloads, config patches, other surfaces' writes) never mutate an open, resolved session's values.
4. **Strongest-effort default (rank-based)** — when no effort was ever picked for a model, the default is the **strongest variant by rank**: `max > xhigh > high > medium > low > none`; unknown variant names are unranked and lose to any ranked one. Replaces both the forced `firstOrNull()` and the previous attempt's "Auto (null variant)" decision. Accepted consequence: max effort raises token cost per turn. Agent-config `variant` overrides still win over any default.
5. **`model.json` fully out of the pick chain** — JetBrains neither writes nor READS `model.json` for selection/effort (no legacy per-agent override tier, no machine-global effort-map tier). Hard cut, no migration: existing global picks are simply ignored; the project store starts empty and the user re-picks once per project. Favorites keep their existing shared write path (separate feature, not part of this chain).

## Out of scope (this round)

- Route pinning, route presets, parameter-lock UI (the previous KILO-8.4 scope) — dropped; small changes that work first.
- New Worktree dialog overhaul beyond the minimal write/read rerouting it needs.
- Any change to the CLI, `model.json` schema, RPC contracts, or the TUI/VS Code surfaces.

## The route (map)

| # | Step | State | Role |
|---|---|---|---|
| 1 | Project parameter store service | **Next** | store service + round-trip tests |
| 2 | Selection chain wiring + `model.json` decoupling | Blocked by 1 | store tier on the chain, writes to the store, read tiers removed, background re-resolution narrowed |
| 3 | Strongest-effort default | Blocked by 2 | rank-based strongest variant replaces lowest-first in the controller and the New Worktree dialog |

**Next ticket: 1.**

## Ticket convention

Each sub-ticket reiterates its slice of the decided design and lists its known gaps / open questions — the implementing session grills them first (backlog.md → "Grouping conventions").

## Verification (epic-level)

`./gradlew typecheck` and `./gradlew test` green in the JetBrains plugin; manual check: two IDE projects open side by side — a model/effort change in one leaves the other untouched; a fresh session in a project starts on that project's picks with the strongest effort preselected.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Project store persists picks per IntelliJ project (workspace.xml), never in the machine-global model.json
- [ ] #2 Sessions inherit the project's picks; in-session values are not mutated by background events
- [ ] #3 Never-picked effort defaults to the strongest variant by rank (max > xhigh > high > medium > low > none)
- [ ] #4 model.json is neither written nor read for model/effort selection by the plugin
- [ ] #5 Two projects side by side: a change in one does not affect the other
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-13 18:57
---
Route-map ticket ids: step 1 = kilo-14.1 (project parameter store service, **Next**), step 2 = kilo-14.2 (wire the selection chain to the project store; drop model.json tiers, blocked by 1), step 3 = kilo-14.3 (strongest-effort default, blocked by 2). Dependencies are wired natively (14.2 depends on 14.1; 14.3 depends on 14.2).
---
<!-- COMMENTS:END -->
