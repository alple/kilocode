---
id: KILO-8.3
title: 'JetBrains: per-project parameter store + session selection wiring'
status: In Progress
assignee:
  - dev1
created_date: '2026-10-06 13:05'
updated_date: '2026-10-07 07:52'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation plan (recorded 2026-10-07, after code research)

Verified all ticket anchors against the current tree; drift ≤ a few lines. New facts: `ai.kilocode.client.*` lives exclusively in the frontend module (store goes to `frontend/src/main/kotlin/ai/kilocode/client/deluxe/`); no `<projectService>` XML registrations exist anywhere — services are annotation-registered (`@Service` + `@State`), `KiloLogSettingsService` has no XML entry; `com.intellij.openapi.components.StoragePathMacros.WORKSPACE_FILE` is available in `intellij.platform.projectModel.jar`.

### 1. New: project parameter store (`ai.kilocode.client.deluxe`, frontend module)
- `frontend/src/main/kotlin/ai/kilocode/client/deluxe/KiloProjectParameterStore.kt` — light project service (`@Service(Service.Level.PROJECT)`) + `@State(name = "KiloParameterStore", storages = [Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)])`, `PersistentStateComponent<State>`. `State`: `model: Map<String,String>` (agent → "provider/model"), `variant: Map<String,String>` ("provider/model" → effort), `route: Map<String,String>` ("provider/model" → routing tag), `agent: String?` (remembered mode — replaces `KiloPluginSettings.AGENT_KEY`). Copy-on-write state (`state = state.copy(...)`), null-free maps. API: `model(agent)`, `setModel(agent, provider, modelId)`, `clearModel(agent)`, `variant(key)`, `setVariant(key, value)`, `route(key)`, `setRoute(key, value)`, `clearRoute(key)`, `getAgent()`, `setAgent(name)`. No XML registration (annotation-only, matches plugin convention).

### 2. SessionSelection.kt (mechanical)
- Delete dead pre-READY `ProvidersDto.defaults` branch (`:36-41`) → `if (!ready) return first` (verified: defaults values are bare model IDs, `modelSelection()` never parses them; the branch always yielded `first`).
- `resolveSessionModel` + private `resolveModelSelection` gain a project-store tier named `stored`, winning over the model.json `override`: chain becomes stored → override(model.json, read-only legacy) → mode → global → recent → fallback. `config == null` early branch: stored → saved → default.

### 3. SessionController.kt (frontend)
- Constructor gains `store: KiloProjectParameterStore` (explicit param; SessionUi + test base pass it).
- New session-local route pins: `prefRoute` / `prefRouteKey` (mirrors prefVariant) + private `routeFor(key)`.
- Writes move into the store: `selectAgent` → `store.setAgent` (was `KiloPluginSettings.setAgent` :863); `seedAgent` reads `store.getAgent()` (:2483); `selectModel` gains `store.setModel` (in-session picks persisted per project — new behavior); `clearModelOverride` gains `store.clearModel(agent)` (reset = explicit return to default); `selectVariant` gains `store.setVariant(key, value)`; new `selectRoute(value: String?)` (null = Auto → clears pin + store entry).
- `applySelection`: `PromptSelection` gains `route: String?`; mapped into prefRoute pins (dialog route pick lands in KILO-8.4; field is a drop-in).
- `selectResolvedModel` variant chain (:2441-2445): pref → `store.variant(key)` → model.json variant map → Auto (drop forced `firstOrNull()`; never-picked effort = null).
- Narrowed re-resolution: background re-fires (AppChanged :1194, app.models :1204, WorkspaceChanged :1262) call a guarded `syncUnresolvedSelection()` that runs `syncModelSelection()` only while `model.model == null` — completes a new session's bootstrap, never mutates a resolved selection. Explicit actions (selectAgent :866, applySelection :945, history sync :2502, message sync :2523) keep plain `syncModelSelection()`.
- `promptDto`: `route = select?.route ?: prefRoute?.takeIf { prefRouteKey == key } ?: key?.let(store::route)` (key = "provider/model" actually being sent).
- `retryPrompt` / `retryPromptCurrent`: fill `route` from the same pin→store chain (`routeFor`).
- Doc comment on `selectAgent` updated (pick now lives per-project, not in KiloPluginSettings).

### 4. NewWorktreeDialog.kt (mechanical)
- Constructor gains `store` (default `project.service<KiloProjectParameterStore>()`).
- `wirePickers`: `app.selectModel(...)` (:277) → `store.setModel(...)`; `app.selectVariant(...)` (:283) → `store.setVariant(...)`.
- `applyModels` (:303): seed read → `store.getAgent()`.
- `syncSelection` (:338): pass store tier into `resolveSessionModel`.
- `syncReasoning` forced `firstOrNull()` stays: the dialog always surfaces an explicit effort default for the new session (pick travels as PromptSelection.variant); Auto-row UI is KILO-8.4 scope.

### 5. model.json write path removal ("stops writing model.json")
- Delete: `KiloBackendModelStateManager.selection()/variant()`; `KiloAppRpcApi.updateModelSelection/updateModelVariant` (shared RPC interface) + impls in `KiloAppRpcApiImpl`; `KiloAppService.selectModel/selectVariant`; `FakeAppRpcApi.updateModelSelection/updateModelVariant` + `selections`/`variants` lists; DTOs `ModelSelectionUpdateDto`/`ModelVariantUpdateDto` (ModelStateDto.kt :27/:34 — unused elsewhere, grep-verified). `ModelStateDto`, `favorite()`, `buildModelStateJson` overloads stay (read tier + favorites).
- `KiloPluginSettings`: remove `AGENT_KEY` + getAgent/setAgent/unsetAgent (mode moved per-project; no migration — old key simply stops being read).

### 6. Per-turn transport (shared DTO + parser)
- `ChatDto.kt`: `PromptDto` gains `route: String? = null` (after variant; @Serializable default — binary-safe).
- `KiloCliDataParser.buildPromptJson`: emit `"route"` after `"variant"` when non-null. `buildCommandJson` untouched — CLI `CommandInput` (prompt.ts:2576-2606) has no `route` field, sending it would be an excess payload key (KILO-8.2 deliberately skipped commands).
- `ChatLogSummary.prompt(PromptDto)`: add `route=` for log parity with variant.
- CLI side needs no change: `PromptInput.route` already exists (prompt.ts:2524, KILO-8.2) and `PromptPayload` spreads it; `KiloRoute.resolve` reads `input.model.api.npm` with model guaranteed non-null at request.ts:115, so the plugin only sends `route` alongside a model.

### 7. Tests
- New `frontend/src/test/kotlin/ai/kilocode/client/deluxe/KiloProjectParameterStoreTest.kt` (plain JUnit, direct instantiation like KiloLogSettingsServiceTest): round-trip via getState/loadState, per-agent model set/clear, variant/route per key, agent get/set.
- Update `SessionControllerTestBase`: fresh store instance per test (`protected lateinit var store`), passed into `controller()`; `KiloPluginSettings.unsetAgent()` calls → fresh store instead.
- Update: ConfigSelectionTest (agent asserts → store; selectVariant-2nd-session now Auto; selectAgent-saved-model test still via model.json tier), SessionControllerSelectionTest (appRpc.selections asserts → store asserts), SessionCreationTest, SessionRetryTest, PromptLifecycleTest (KiloPluginSettings.getAgent → store), NewWorktreeDialogTest (store writes instead of app.selectModel/selectVariant), HistoryLoadingTest, TurnLifecycleTest.
- New controller tests: store tier on top of chain; model.json legacy tier still read when store empty; pre-READY degrades to first picker item; Auto-when-never-picked; background events don't re-resolve a resolved session but complete an unresolved one; in-session picks persist to store + leak nothing to other open sessions; route pin rides prompt + retries; selectRoute(null) clears.
- `KiloCliDataParserTest`: route emitted after variant (present + absent cases); ChatDtoSerializationTest: route round-trip; KiloBackendModelStateManagerTest: drop selection/variant cases.

### Interpretation notes (follow directly from ADR-0002/KILO-8 goal, recorded for review)
- Store variant tier sits between session pin and model.json variant map — the ticket's "pref → model.json map → Auto" sentence omits the store, but "new sessions resolve fresh from the store" requires it.
- Dead model.json write chain (RPC methods incl. shared interface, service, manager, DTOs) is deleted, not just uncalled — keeps the "JetBrains stops writing model.json" constraint real.
<!-- SECTION:PLAN:END -->
