---
id: KILO-8.3
title: 'JetBrains: per-project parameter store + session selection wiring'
status: Testing
assignee:
  - dev1
created_date: '2026-10-06 13:05'
updated_date: '2026-10-07 08:39'
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

### Deviation from the recorded plan (executed 2026-10-07)
- The narrowed background re-resolution needed more than a `model.model == null` guard: a blank session can resolve from config before the model.json tier loads, and an opened-but-unresolved session (empty history) must still complete its bootstrap from the store. Final shape: `syncBackgroundSelection()` — opened sessions with a resolved selection never re-resolve; a blank (or unresolved) session re-resolves only when the chain-relevant tier data (`model`/`variant`/`recent` maps, config) changed since its last resolution — so a favorites refresh or another surface's pick never mutates a resolved selection, while late bootstrap arrivals complete it once. Opened sessions additionally get `refreshVariants()` (variant-list validity only) on catalog refresh; without it the SessionRetryTest effort flow broke (selectVariant early-returns on an empty stale list).
- `ChatDtoSerializationTest` lives in backend (`backend/src/test/kotlin/ai/kilocode/backend/cli/`), not shared; route round-trip tests added there.
- PromptSelection route mapping kept; dialog syncReasoning reads the store variant tier first, then the model.json map, then the forced display default (kept per plan).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Background-event guard design took two iterations: a plain `model.model == null` guard regressed tests where the model.json tier loads after the config-tier resolution (ConfigSelectionTest tier scenarios), and a `ref != null` guard broke SessionRecoveryTest (open empty-history sessions never bootstrap). The final tier-growth comparison (`tierShape(model/variant/recent) + config`) satisfies both; documented on `syncBackgroundSelection`.

TitleButtonTest (2 failures) and the detekt baseline were confirmed pre-existing by stashing the change and re-running on the clean tree.

Favorite-only model.json changes are deliberately NOT treated as bootstrap growth (compared via chain-relevant fields only) — otherwise a favorites refresh would leak another session's store pick into an open session (ConfigSelectionTest selectModel case).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented the per-project parameter store and session selection wiring for the JetBrains plugin (ADR-0002, KILO-8.3).

**What changed**

- **New store** (`frontend/src/main/kotlin/ai/kilocode/client/deluxe/KiloProjectParameterStore.kt`): the plugin's first project-level light service — `@State` into workspace.xml (`RoamingType.DISABLED`), state = model pick per agent ("provider/model"), reasoning effort and OpenRouter route per (provider, model), remembered mode/agent. All JetBrains parameter writes now go here: in-session model/effort/route/mode picks, New Worktree dialog picks, and reset-to-default (clears the agent's stored pick). `KiloPluginSettings.kilo.session.agent` and its PropertiesComponent accessors are removed; the old key is simply no longer read (no migration).
- **model.json is now read-only for this plugin**: the whole selection/variant write path was deleted — `KiloAppService.selectModel/selectVariant`, shared RPC `KiloAppRpcApi.updateModelSelection/updateModelVariant`, `KiloAppRpcApiImpl` overrides, `KiloBackendModelStateManager.selection()/variant()`, and the `ModelSelectionUpdateDto`/`ModelVariantUpdateDto` DTOs. Favorites keep their existing cross-surface write path (per ADR-0002's carve-out).
- **Model chain** (`SessionSelection.kt`): the project store tier sits on top of the five existing tiers (store → model.json per-agent override (read-only legacy) → per-agent config → global config → recent → kilo-auto). The dead pre-READY `ProvidersDto.defaults` branch is deleted — bootstrap degrades to the first picker item. The forced `firstOrNull()` variant tier is gone in `SessionController.selectResolvedModel`: chain is session pin → store effort → model.json variant map → Auto (null = provider default).
- **On-demand-only re-resolution** (`syncBackgroundSelection`): opened sessions with a resolved selection are never re-resolved by background events (catalog reload, config patch, model-state refresh, another session's pick); a blank or unresolved session re-resolves only when chain-relevant tier data changed since its last resolution, so late bootstrap arrivals complete it exactly once. Opened sessions get variant-list-only refresh on catalog changes (`refreshVariants`). Explicit actions (pick, agent switch, applySelection, history/message sync) resolve as before.
- **Per-turn route transport**: `PromptDto` gains `route: String?`, emitted by `buildPromptJson` after `variant`; `promptDto` and both retry paths fill it from the session pin (`prefRoute`, keyed to the sent model) falling back to the store; new `selectRoute(value?)` pins/clears (null = Auto). Commands still carry no route (CLI `CommandInput` has no such field — verified). `ChatLogSummary` logs `route=` for parity. No CLI change (KILO-8.2's `PromptInput.route` consumes it as-is).

**Tests**

- New: `KiloProjectParameterStoreTest` (round-trip, per-agent model set/clear, per-key variant/route, blank-value filtering, fresh-empty state); `ParameterStoreSelectionTest` (16 cases: store tier over model.json, legacy tiers when store empty, Auto-when-never-picked, store inheritance into the next session without model.json writes, background-event stability for opened sessions + bootstrap completion for blank ones, route pin/clear/inheritance in prompts and both retry paths).
- Updated: `ConfigSelectionTest`, `SessionControllerSelectionTest`, `SessionCreationTest`, `SessionRetryTest`, `PromptLifecycleTest`, `NewWorktreeDialogTest` (store instead of model.json writes; Auto assertions), parser/serialization route cases, `SessionControllerTestBase` (fresh store per test), `KiloBackendModelStateManagerTest` (write-path cases removed).

**Verification**: `./gradlew typecheck` — all modules green; `./gradlew test` — shared + backend green, frontend 4418 tests with only the 2 pre-existing `TitleButtonTest` failures (verified failing on the clean tree too — environment-dependent font metrics). Detekt is a pre-existing failing baseline (same counts on the clean tree), not part of this package's gates. A changeset was added (`.changeset/jetbrains-per-project-parameter-store.md`).

**Risks/follow-ups**: KILO-8.4 (picker UI + staleness notice) is unblocked — it adds the route picker UI against `selectRoute` and the `PromptSelection.route` drop-in. The never-picked-effort-Auto change surfaces in the reasoning picker as no preselected row until 8.4 lands the Auto row.
<!-- SECTION:FINAL_SUMMARY:END -->
