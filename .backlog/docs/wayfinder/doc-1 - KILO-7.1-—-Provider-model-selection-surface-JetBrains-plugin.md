---
id: doc-1
title: KILO-7.1 — Provider/model selection surface (JetBrains plugin)
type: other
created_date: '2026-10-06 11:09'
tags:
  - 'wayfinder:research'
  - jetbrains
---
Ticket: KILO-7.1 · Date: 2026-10-06 · Type: research findings

# KILO-7.1 — Provider/model selection surface (JetBrains plugin)

All paths are relative to the repo root. Every claim carries a `file:line` citation. Line numbers are from the working tree as of 2026-10-06.

> **Correction (2026-10-06, user):** "upstream" in this effort means the **Kilo Code repo** (`Kilo-Org/kilocode`) — this repo is the Deluxe edition fork of Kilo Code. §5's "no opencode upstream counterpart" phrasing is accurate only relative to opencode; `packages/kilo-jetbrains/` IS synced from Kilo Code, so upstream-conflict surface applies in full. Strategy for any change: prefer Deluxe-owned implementations; keep edits inside upstream UI files minimal.

## 1. Data contract

### 1.1 Wire DTOs (shared module)

`ProvidersDto` — the workspace provider catalog the frontend receives:

- `providers: List<ProviderDto>`, `connected: List<String>`, `defaults: Map<String, String>` — `packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/dto/ProviderDto.kt:92-96`.
- `ProviderDto(id, name, source, models: Map<String, ModelDto>)` — ProviderDto.kt:84-89.
- `ModelDto` carries the full model card: prices, `contextLength`, `releaseDate`, `latest`, capability booleans (`attachment`, `reasoning`, `toolCall`, `free`, `byok`), `status`, `recommendedIndex`, `variants: List<String>` (reasoning variants), `limit`, `cost`, `capabilities`, `options.description`, `autoRouting.models`, `terminalBench` score, `mayTrainOnYourPrompts` — ProviderDto.kt:6-30, plus `ModelLimitDto` :33-37, `ModelCostDto`/`ModelCacheCostDto` :40-50, `ModelCapabilitiesDto`/`ModelInputCapabilitiesDto` :53-65, `ModelOptionsDto` :68-70, `ModelAutoRoutingDto` :73-75, `ModelTerminalBenchDto` :78-81.

`ModelSelectionDto` — a bare provider/model pair:

- `providerID: String, modelID: String` — `packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/dto/ModelStateDto.kt:6-9`.
- `ModelStateDto` = `{ favorite: List<ModelSelectionDto>, model: Map<String, ModelSelectionDto> /* keyed by agent */, variant: Map<String, String> /* keyed by "provider/model" */, recent: List<ModelSelectionDto> }` — ModelStateDto.kt:12-17.
- Update payloads: `ModelFavoriteUpdateDto(action, providerID, modelID)` :20-24, `ModelSelectionUpdateDto(agent, providerID, modelID)` :27-31, `ModelVariantUpdateDto(key, value)` :34-37.

`ProviderSettingsDto` — the Providers *settings* page payload (separate, richer shape):

- `providers: List<ProviderSettingsProviderDto>`, `connected`, `defaults`, `auth: Map<providerId, List<ProviderAuthMethodDto>>`, `config: Map<providerId, CustomProviderConfigDto>`, `disabled`, `enabled`, `disabledScopes`, `enabledScopes`, `errors` — `packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/dto/ProviderSettingsDto.kt:6-17`.
- `ProviderSettingsProviderDto` adds `description`, `key`, `metadata (noteKey/icon/priority)` and a computed `custom` flag (`source == "custom" || id in setOf("openai-compatible")`) — ProviderSettingsDto.kt:20-30, 33-37.
- Connect/OAuth/disconnect/enable payloads: `ProviderConnectDto` :64-69, `ProviderOAuthAuthorizeDto` :72-77, `ProviderOAuthCallbackDto` :80-85, `ProviderOAuthReadyDto` :88-93, `ProviderDisconnectDto` :96-99, `ProviderEnableDto` :102-105, `ProviderActionResultDto(state: ProviderSettingsDto, profileCleared, error)` :108-112, custom-provider save/fetch :115-148, `CustomProviderConfigDto` :151-160.

### 1.2 Semantics of `connected`

- On the CLI side, `GET /provider` builds `connected` from `Provider.Service.list()`, which returns the per-instance provider registry `s.providers` — only providers the CLI actually instantiated with credentials/config. The handler comment states: *"connected only contains providers with non-empty models after Provider.Service.list()"* — `packages/opencode/src/server/routes/instance/httpapi/handlers/provider.ts:63,78-79`; `const list = ... InstanceState.use(state, (s) => s.providers)` — `packages/opencode/src/provider/provider.ts:1824`.
- `connected` is a list of provider **IDs** (strings), not per-provider data — handlers/provider.ts:104 (`connected: Object.keys(connected)`); schema `connected: Schema.Array(Schema.String)` — provider.ts:1159-1164.
- The JetBrains side consumes it as `connected: List<String>` and uses it purely as a membership filter (`it.id in cfg.connected`) — `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/model/ModelItems.kt:15` and `SessionSelection.kt:84`.

### 1.3 Do non-connected provider catalogs reach the frontend? Yes.

- The CLI `GET /provider` handler merges the **full models.dev catalog** (`ModelsDev.Service.use((s) => s.get())`, filtered by `enabled_providers`/`disabled_providers`) with the connected providers into `providers`, then returns `all` (that merged set) alongside `connected` — handlers/provider.ts:54-107 (esp. :56-72 catalog merge, :99-105 response). Kilo-specific overlays on `all`: anaconda-desktop overlay :56, `kilo` provider removal when anonymous :65-67, `filterPromptTrainingModels` :68-74, zero-model provider pruning + `failed` set :77-86, Kilo `recommend()` override for the kilo default :87-97, `metadata` icons :101.
- The JetBrains backend fetches this endpoint and maps it verbatim: `KiloCliDataParser.parseProviders` reads `all`/`connected`/`default` — `packages/kilo-jetbrains/backend/src/main/kotlin/ai/kilocode/backend/cli/KiloCliDataParser.kt:552-559`; `KiloBackendWorkspace.fetchProviders` issues `GET /provider?directory=...` — `packages/kilo-jetbrains/backend/src/main/kotlin/ai/kilocode/backend/workspace/KiloBackendWorkspace.kt:275-284`; `KiloWorkspaceDtoMapper.providers` maps `ProviderData` (providers=all, connected, defaults) straight into `ProvidersDto` — `packages/kilo-jetbrains/backend/src/main/kotlin/ai/kilocode/backend/rpc/KiloWorkspaceDtoMapper.kt:46-50`.
- So the frontend **does** hold full model catalogs for non-connected providers; it simply filters them out at item-build time (ModelItems.kt:14-15, SessionController.kt:1226-1228). There is no server-side restriction, only the client-side filter.
- The provider list reaches the frontend via the workspace state flow (`KiloWorkspaceStateDto.providers`) — `packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/dto/KiloWorkspaceStateDto.kt:24-34` — and via `KiloWorkspaceRpcApi.models(directory) → ModelsWorkspaceDto` — `packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/KiloWorkspaceRpcApi.kt:52-53`, KiloWorkspaceStateDto.kt:36-41.
- `ProvidersDto.defaults` semantics: the CLI sends `default` as **providerID → default modelID** (`Provider.defaultModelIDs` = first sorted model per provider) — handlers/provider.ts:86, `packages/opencode/src/provider/provider.ts:1189-1191`. Caveat: the JetBrains fallback resolver reads it as if keyed by **agent** (`providers?.defaults?.get(agent)`) and parses values with a `provider/model` splitter — `SessionSelection.kt:38-41,89-93`. Provider IDs never collide with agent names, and bare model IDs without `/` parse to null, so this branch resolves to `first` (first picker item) in practice — flagging as a likely latent mismatch worth a follow-up.

## 2. UI surface

### 2.1 PickerPopup capabilities (`ai/kilocode/client/ui/picker/PickerPopup.kt`)

- Single generic list popup over a `JBList` with: optional `SearchTextField` (:95-97, :230-240), **section headers** via a `sectionTitle: (rows, index) -> String?` callback rendered by the renderer (:67, ModelPicker.kt:221, ModelPickerRows.kt:80-85), optional trailing hit zone (favorite star; `trailingHit`/`onTrailing` :68-69, :254-271, ModelPicker.kt:222-224), an optional east-side details panel with expand/collapse (:72-74, :273-333, ModelPicker.kt:182-231), a `toolbar: List<JComponent>` header slot (:71, :196-212), and `Mode.Single` (auto-close on pick, :65, :277-285) vs `Mode.Multi` (stays open + space key, :82, :245-247).
- **Two-level navigation: not built in.** The popup renders one flat row list; rows come from a caller-supplied closure `rows: (query) -> List<T>` (:60) and `refresh()` re-invokes it (:181-190). There is no navigation stack, back affordance, or breadcrumb.
- A provider-first step **is feasible inside ModelPicker** (not inside PickerPopup itself): `ModelPicker.showPopup()` owns the `rows` closure (ModelPicker.kt:215), so it can swap between a provider-level row builder and a per-provider model row builder and call `popup.refresh()`; the `toolbar` slot could host a back button. The blocker is `Mode.Single`'s `autoClose` — a primary click closes the popup (:277-285) — so a two-step flow would need `autoClose = false` (footer Close appears, :215-228) plus provider-step state in ModelPicker. No existing consumer implements this; only two PickerPopup usages exist overall (ModelPicker.kt:209, ProvidersSettingsUi.kt:917).

### 2.2 ModelPicker and where it is embedded

- `ModelPicker` (PickerButton subclass) is a flat list of `ModelPicker.Item` keyed by `"$provider/$id"` (ModelPicker.kt:29-56, :53). Items are built by `modelItems(providers, includeSmall)` which filters `id == "kilo" || id in connected` and drops small kilo models unless included — ModelItems.kt:12-46 (`KILO_PROVIDER` :5, small-model filter :42). The same function is shared by the models settings page and the New Worktree dialog (doc comment :7-11; usages ModelsSettingsUi.kt:8,114; NewWorktreeDialog.kt:302).
- Row construction and sections: `modelPickerRows` orders Optional-clear → Favorites (only when query blank) → Recommended (`recommendedIndex != null`) → remaining models grouped by provider (kilo first, `providerSort` ModelPicker.kt:332), each labeled with `providerName` — ModelPickerRows.kt:6-49. Search matches `display`, `id`, and `providerName` (acronym search) — ModelPickerRows.kt:16-18, ModelSearch ModelPicker.kt:253-297. Ctrl+2 cycle = favorites else Recommended (`modelCycle`) — ModelPickerRows.kt:56-68, ModelPicker.kt:145-159.
- Session chat embed: pickers (`mode`/`model`/`reasoning`) live in **PromptPanel** (prompt bar), `override val model = ModelPicker().apply { placement = ABOVE; idleFill = null }` — `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/prompt/PromptPanel.kt:148-153`. SessionUi binds them: `prompt.model.onSelect = { item -> prompt.setAttachmentEnabled(item.attachment); controller.selectModel(item.provider, item.id) }`, favorites toggle wired to `app.toggleModelFavorite` — `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionUi.kt:705-724`; on `WorkspaceReady` it re-pushes `controller.model.models` (built from the filtered `ProvidersDto`, SessionController.kt:1226-1246) into the picker — SessionUi.kt:740-749.
- Models settings page: `ModelsSettingsUi` uses `ModelPicker` + `modelItems` for global config defaults (`model`, `small_model`, `subagent_model`, per-agent defaults) and saves via `ConfigPatchDto` — ModelsSettingsUi.kt:30-110 (save path :67-69), content at `ModelsSettingsContent`.
- New Worktree dialog: `NewWorktreeDialog` reuses the same PromptPanel-style pickers; `wirePickers` sets `prompt.model.onSelect = { modelKey = item.key; agent?.let { app.selectModel(it, item.provider, item.id) }; ... }` — `packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/agentManager/worktree/NewWorktreeDialog.kt:271-285`; models loaded per target worktree via `workspaces.models(directory)` :293-298; items from the shared `modelItems` :300-311.
- Also embedded in agent settings: `AgentsConfigurable.items(providers)` — `frontend/src/main/kotlin/ai/kilocode/client/settings/agents/AgentsConfigurable.kt:413`.

### 2.3 ProvidersSettingsUi (connect/disconnect/auth)

- Toolbar: Add custom provider + Refresh — ProvidersSettingsUi.kt:129-140. Content wiring passes the five flows: `ProvidersContent(::connect, ::oauth, ::disconnect, ::enable, ::edit)` — ProvidersSettingsUi.kt:141.
- Implements: API-key connect dialogs, device/OAuth flow with a code extraction regex (`OAUTH_CODE_RE` :82-84), disconnect, enable/disable, custom provider save/edit, and a custom-model multi-select popup built on PickerPopup (CustomModelRenderer :97-119, picker at :917). Inline error helper `customSaveError` :89-95. Companion file list: `ProviderCatalog.kt`, `ProviderListRows.kt`, `ProvidersConfigurable.kt` (settings entry point).

## 3. State and persistence

### 3.1 Path of a pick (`SessionController.selectModel`, SessionController.kt:871-888)

1. Resolves current agent; rejects keys not in the (filtered) item list (:874-876, `item(key)` :2449).
2. Stores the pick in **session-local in-memory prefs**: `prefModel = "provider/id"`, `prefAgent`, `prefOverride = true`, clears variant prefs (:877-882). These fields are declared at :208 and win over persisted state during workspace-ready re-resolution (`messageSelection` :2451-2454).
3. Fires `SessionControllerEvent.WorkspaceReady` with `selectResolvedModel(key)` → sets `model.model` on `SessionModel` and refreshes header + variants (:883-886, :2437-2447). `SessionModel` fields: `model`, `defaultModel`, `modelOverride`, `variants`, `variant` — `frontend/src/main/kotlin/ai/kilocode/client/session/model/SessionModel.kt:62-69`.
4. Sends telemetry ("Model Selected") :887.

- Reaching the CLI: the selection travels **per turn, not as global state** — `buildPrompt` puts `model.model` into `PromptDto(providerID, modelID, agent, variant, ...)` (SessionController.kt:2384-2404), sent via `KiloSessionRpcApi.prompt(id, directory, prompt)` — `packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/KiloSessionRpcApi.kt:108-112`. A comment at SessionController.kt:847-851 documents the deliberate rule that picks must not be written to the CLI's global config (it disposes every instance and kills running turns).
- `WorkspaceReady` is a **frontend controller event**, not an RPC (fired locally in selectAgent/selectModel; SessionController.kt:864, :883). `KiloPluginSettings` (PropertiesComponent) persists only the **agent/mode**, never a model — `frontend/src/main/kotlin/ai/kilocode/client/plugin/KiloPluginSettings.kt:21-24`, and selectModel does not touch it.
- In-session picks are therefore **per-session** (in-memory prefs + PromptDto). On session open the default resolves from the persisted per-agent model + config + recents (`syncModelSelection` :2406-2423, `resolveSessionModel`/`resolveSessionDefaultModel` in SessionSelection.kt:22-77; fallback `kilo/kilo-auto/free` :9-10,70).

### 3.2 The one persisted model selection: `model.json` (per-agent, machine-global)

- Who persists in the JetBrains plugin: **only the New Worktree dialog** calls `app.selectModel(agent, providerID, modelID)` (NewWorktreeDialog.kt:277) — the only production call site of `KiloAppService.selectModel` besides SessionUi's `controller.selectModel` (SessionUi.kt:710, which does not persist). `KiloAppService.selectModel` optimistically updates local state then RPCs `updateModelSelection(ModelSelectionUpdateDto(agent, providerID, modelID))` — `frontend/src/main/kotlin/ai/kilocode/client/app/KiloAppService.kt:287-298` (favorites :266-285, variants :300-311).
- The backend RPC impl (`KiloAppRpcApiImpl.modelState/updateModelFavorite/updateModelSelection/updateModelVariant`) delegates to `KiloBackendAppService.models` — `backend/src/main/kotlin/ai/kilocode/backend/rpc/KiloAppRpcApiImpl.kt:83-101`. The service interface is `KiloAppRpcApi.modelState()/updateModel*` — `shared/src/main/kotlin/ai/kilocode/rpc/KiloAppRpcApi.kt:64-73`.
- `KiloBackendModelStateManager` reads/writes **`~/.local/state/kilo/model.json` directly on disk** (it resolves the dir via the CLI's `GET /path` endpoint, KiloBackendModelStateManager.kt:100-121; `DEFAULT_DIR` :25; selection merge keyed by agent :66-73; variant map keyed by `"provider/model"` :75-81). There is no CLI write endpoint for selection — the backend does the file mutation itself and reparses via `KiloCliDataParser.parseModelState/buildModelStateJson` (KiloCliDataParser.kt:868-894, writes all four keys :889-892).
- Scope: **per-agent** (key `model[agent]`), **global per user** (not per-worktree, not per-session) — same file the CLI itself uses: `packages/opencode/src/kilocode/config/model-state.ts:51` (`path.join(Global.Path.state, "model.json")`), schema `{ model: record<agent, Ref>, recent, favorite, variant }` :17-23. Also referenced by: `packages/opencode/src/provider/provider.ts:2151` (`defaultModel` falls back to the `recent` list), `packages/opencode/src/cli/cmd/run/variant.shared.ts:19`, `packages/opencode/src/kilocode/tool/task.ts:184`, `packages/opencode/src/kilocode/plan-followup.ts:217`.
- CLI-side consumers of that state: `KilocodeModelState.get()/update()` — `kilocode/config/model-state.ts:30-48` (update only supports a `favorite` patch :25-28); served/updated through the CLI's own kilo HTTP handler `packages/opencode/src/kilocode/server/httpapi/handlers/config-console.ts:188-195`; used to seed cloud defaults `kilocode/cloud/defaults.ts:22`. Note the CLI itself does **not** persist the `model[agent]` selection record anywhere in-repo besides the TUI writing the same file (see §5) — the record is client-maintained.
- The TUI keeps the same contract: `local.tsx` models `model: Record<name, {providerID, modelID}>`, `recent`, `favorite` and reads/writes `paths.state/model.json` — `packages/tui/src/context/local.tsx:147-199` (path :185).

## 4. Contract boundary — every endpoint in the round trip

### 4.1 Frontend ↔ backend (JetBrains split-mode fleet RPC)

| Endpoint | File |
|---|---|
| `KiloWorkspaceRpcApi.state(directory) → Flow<KiloWorkspaceStateDto>` (carries `ProvidersDto`) | shared/.../rpc/KiloWorkspaceRpcApi.kt:44; KiloWorkspaceStateDto.kt:24-34 |
| `KiloWorkspaceRpcApi.models(directory) → ModelsWorkspaceDto` (settings + new-worktree) | KiloWorkspaceRpcApi.kt:53; KiloWorkspaceStateDto.kt:36-41 |
| `KiloSessionRpcApi.prompt(id, directory, prompt: PromptDto)` — carries provider/model/agent/variant per turn | KiloSessionRpcApi.kt:109; PromptDto fields at SessionController.kt:2396-2403 |
| `KiloAppRpcApi.modelState()/updateModelFavorite/updateModelSelection/updateModelVariant` | KiloAppRpcApi.kt:64-73; impl KiloAppRpcApiImpl.kt:83-101 |
| `KiloProviderRpcApi.state/connect/authorize/callback/disconnect/enable/saveCustom/fetchCustomModels` | shared/.../rpc/KiloProviderRpcApi.kt:29-36 |

### 4.2 Backend ↔ CLI (HTTP against `kilo serve`)

| Call | Evidence |
|---|---|
| `GET /provider?directory=...` — catalog (`all`, `connected`, `default`, `failed`) | KiloBackendWorkspace.kt:277; CLI handler packages/opencode/src/server/routes/instance/httpapi/handlers/provider.ts:54-108 |
| `GET /provider/auth?directory=...` — auth methods per provider | KiloBackendProviderSettingsManager.kt:51 |
| `PUT /auth/:providerID` — store API key (connect) | KiloBackendProviderSettingsManager.kt:89, :202 |
| `POST /provider/:id/oauth/authorize`, `POST /provider/:id/oauth/callback` | KiloBackendProviderSettingsManager.kt:96, :103; CLI handlers provider.ts:110-158 |
| `DELETE /auth/:providerID` — disconnect | KiloBackendProviderSettingsManager.kt:296 |
| `GET /path` — resolve `Global.Path.state` dir for model.json | KiloBackendModelStateManager.kt:104; parser KiloCliDataParser.kt:850 |
| Direct file read/write of `<state>/model.json` (selection/favorites/variants) | KiloBackendModelStateManager.kt:46-98 |

### 4.3 What a new capability would require on the CLI side

- **Selectable models from a non-connected provider**: **no CLI change needed.** The catalog for every enabled provider is already in `all` (§1.3); the restrictions are client-side only (`ModelItems.kt:15`, `SessionController.kt:1226-1228`, `validModelSelection` SessionSelection.kt:84). Caveat: actually *running* an unconnected provider needs credentials, which is the Providers connect flow, not the catalog.
- **Provider-level default** (a default scoped to a provider rather than an agent): the persisted-state schema has no provider dimension — `KilocodeModelState.State.model` is keyed by agent (kilocode/config/model-state.ts:17-23) and the JetBrains `ModelStateDto.model` mirrors it (ModelStateDto.kt:14). Files that would need changes: CLI `packages/opencode/src/kilocode/config/model-state.ts` (schema + patch), consumers `packages/opencode/src/kilocode/cloud/defaults.ts:22`, `packages/opencode/src/kilocode/server/httpapi/handlers/config-console.ts:188-195`, `packages/opencode/src/provider/provider.ts:2146-2179` (defaultModel), plus the JetBrains mirrors `packages/kilo-jetbrains/shared/.../dto/ModelStateDto.kt`, `backend/.../cli/KiloCliDataParser.kt:868-894`, and `backend/.../app/KiloBackendModelStateManager.kt`.
- If per-worktree defaults were wanted instead, note model.json is user-global; no directory dimension exists anywhere in the chain (§3.2).

## 5. Parity — TUI and VS Code

- **CLI TUI** (`packages/tui/src/component/dialog-model.tsx`): flat `DialogSelect` list grouped into sections — Favorites, Recent, Recommended (`kilo` models with `recommendedIndex`, only when signed in), then per-provider sections; selection unit is the `providerID/modelID` pair (buildModelPickerOptions, `packages/tui/src/kilocode/model-picker.ts:97-186`; `KILO_PROVIDER_ID` :18; category rules :159-163). Search = fuzzysort over `title/category/providerName/providerID/modelID` (:58-66). No provider-first step inside the model dialog; instead an action row links to `DialogProvider` ("Connect provider" / "View all providers") — dialog-model.tsx:138-155. Non-connected providers surface as "Popular providers" section when signed out (:93-101). Variant pick (`dialog.replace(DialogVariant)`) is a second step *after* model choice (:118-131). Pure grouping logic is unit-testable in `kilocode/model-picker.ts`.
- **VS Code (sidebar + Agent Manager)**: `ModelSelector` popover (SolidJS, virtualized) groups rows as Favorites / Auto / Recommended / Most-used, then per-provider groups sorted by `providerSortKey`; rows keyed `providerID/modelID` — `packages/kilo-vscode/webview-ui/src/components/shared/ModelSelector.tsx:57-73, 320-392`; utils in `model-selector-utils.ts:47`. Same as TUI: flat grouped list, no provider-first navigation. Agent Manager reuses the sidebar ChatView/selector (per package AGENTS.md architecture notes); its New Worktree flow additionally has `MultiModelSelector` for allocating N parallel worktrees per model (`packages/kilo-vscode/webview-ui/agent-manager/MultiModelSelector.tsx:35-177`) and `new-worktree-models.ts:15` (kilo readiness check) — still per-model selection, provider used only for grouping/filtering.
- **Consistency summary**: all three surfaces agree on the `providerID/modelID` selection unit, favorites + recommended sections, and kilo-first ordering; none has a provider-first drill-down. JetBrains and TUI additionally persist per-agent defaults to the shared `model.json`; VS Code has no per-agent model.json record surfaced in this research.

## Open follow-ups surfaced

- `ProvidersDto.defaults` key/value semantics mismatch (provider-keyed on the wire, agent-keyed read in `SessionSelection.kt:38-41`) — likely dead/broken fallback branch, verify intent.
- Only the New Worktree dialog persists a per-agent model on the JetBrains side; the in-session picker relies on in-session prefs and would not survive IDE restarts (agent does, via KiloPluginSettings).
