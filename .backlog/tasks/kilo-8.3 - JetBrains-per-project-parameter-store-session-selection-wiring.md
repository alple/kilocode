---
id: KILO-8.3
title: 'JetBrains: per-project parameter store + session selection wiring'
status: In Progress
assignee:
  - dev1
created_date: '2026-10-06 13:05'
updated_date: '2026-10-07 07:37'
labels: []
dependencies: []
parent_task_id: KILO-8
priority: high
type: task
ordinal: 23000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Decisions implemented

[Decide what a new session starts with (model/route/effort defaults)](KILO-7.4) in full; [ADR-0002](docs/adr/0002-per-project-parameter-store.md). **Favorites clarification (decided 2026-10-06): favorites keep the existing shared `updateModelFavorite` → model.json path (cross-surface with TUI/VS Code); "JetBrains stops writing model.json" applies to selection/variant/route/mode writes — ADR-0002 gets a one-line clarification.**

## Scope

**1. Project parameter store** — Deluxe-owned project-level `PersistentStateComponent` in the new `ai.kilocode.client.deluxe` package (workspace.xml storage; `KiloLogSettingsService` is the PersistentStateComponent pattern precedent but is app-level — this is the plugin's first project-level store):
- Model pick per agent; reasoning effort per (provider, model); route per (provider, model); remembered **mode/agent** — moved out of IDE-global `KiloPluginSettings` (`kilo.session.agent`) so nothing cross-populates between IntelliJ projects. Per-worktree NOT in play (Agent Manager worktree sessions belong to the project).
- **ALL JetBrains parameter writes go there**: in-session picks, New Worktree dialog (`app.selectModel`/`app.selectVariant` at `NewWorktreeDialog.kt:277/283` switch from model.json to the store), reasoning/route picks, mode switch (`SessionController.kt:863` write; reads `seedAgent :2483`, `NewWorktreeDialog.kt:303`).
- JetBrains **stops writing** `model.json` for selection/variant/route/mode (`KiloBackendModelStateManager.selection()/variant()` no longer called from these flows); model.json stays a **read tier** (per-agent override tier 1, `model.json` variant map as lower effort read tier, favorites). TUI keeps its own writes.

**2. Session selection wiring**:
- Model chain: 5 tiers kept, **project store tier on top** (0: project store model per agent → 1: per-agent `model.json` override (read-only legacy) → 2: per-agent config default → 3: global config default → 4: recent → 5: `kilo/kilo-auto/free`) — `SessionSelection.kt` `resolveSessionModel`/`resolveModelSelection` (:45-77).
- **Delete the dead pre-READY `ProvidersDto.defaults` branch** (`SessionSelection.kt:36-41`; wire is providerID → bare model ID, unparseable — verified) → bootstrap degrades to the first picker item.
- **Drop the forced `firstOrNull()` variant tier** (`SessionController.kt:2445`) — never-picked effort starts **Auto** (null variant = provider default); `model.json` variant map stays a lower read tier (`selectResolvedModel` chain :2441-2445 becomes pref → model.json map → Auto).
- **On-demand-only re-resolution**: narrow `syncModelSelection` re-fires — background events (`AppChanged` :1194, models :1204, `WorkspaceChanged` :1262) never mutate an open session's values; explicit user actions kept (selectAgent :866, applySelection :945, history/message sync :2502/:2523). New sessions resolve fresh from the store.
- **Per-turn send**: route + effort pins ride `PromptDto` (`ChatDto.kt:135-145`) → `KiloCliDataParser.buildPromptJson` (`:910-943`) → CLI `PromptInput` (field added by the CLI route-support ticket; additive on both sides, no cross-dependency) — fill in `promptDto` (`:2379-2404`), including retry paths (`:2319-2346`). Route/effort source: session-local pin state (pref*) falling back to the store.

## Hard constraints

- **No `ModelStateDto`/`model.json` schema change; no CLI schema change from this ticket** (the CLI transport field lives in the CLI ticket).
- Fork strategy: store lives in `ai.kilocode.client.deluxe`; edits to `SessionSelection.kt`/`SessionController.kt`/`NewWorktreeDialog.kt` kept minimal and mechanical.
- Lock interplay: new sessions start unlocked; inherited values are ordinary editable values; freeze/lock UI lands with the parameter component ticket.

## Verification

- Store service tests (precedent: `KiloLogSettingsServiceTest`); SessionController tests via `SessionControllerTestBase` for tier chain, dead-branch removal, Auto-when-never-picked, and narrowed re-resolution; `KiloCliDataParserTest` for the prompt JSON mirror.
<!-- SECTION:DESCRIPTION:END -->
