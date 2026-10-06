---
id: KILO-7.1
title: Map the existing provider/model selection surface in the JetBrains plugin
status: Done
assignee:
  - dev1
created_date: '2026-10-06 07:27'
updated_date: '2026-10-06 11:12'
labels:
  - 'wayfinder:research'
dependencies: []
parent_task_id: KILO-7
priority: high
type: spike
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

What exactly exists today for provider+model selection in `packages/kilo-jetbrains/`, and what would any new provider-level choice need from the CLI contract?

Facts to establish (verified, with file/line evidence):

1. **Data**: what `ProvidersDto` / `ModelSelectionDto` / `ProviderSettingsDto` carry; the semantics of `cfg.connected`; whether disconnected providers' model catalogs are available to the frontend at all.
2. **UI**: how `ModelPicker` + `PickerPopup` + `ModelPickerRenderer` are built (sections, search, favorites, details panel, cycling via Ctrl+2); what `ProvidersSettingsUi` offers for connect/disconnect/auth; how the session header and New Worktree dialog embed pickers.
3. **State/persistence**: `SessionController.selectModel` — where the selection lands (model store, config `model` key?), per-session vs per-agent vs per-worktree.
4. **Contract boundary**: which parts of this flow call back into the CLI (RPC endpoints/DTOs) — i.e., what would need a CLI-side change if providers beyond `connected` (or provider-level defaults) must be selectable.
5. **Parity check**: how the CLI TUI and VS Code surfaces treat the same problem (grouping exists there too) — enough to judge consistency costs, not to design them.

Deliverable: a findings doc + the facts that the UX and shape decisions hang on.
<!-- SECTION:DESCRIPTION:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-06 07:34
---
Findings doc: `.kilo/plans/wayfinder/kilo-7.1-selection-surface.md` (all claims with file:line evidence).

Condensed findings:
1. DATA: ProvidersDto = providers(all)/connected/defaults (ProviderDto.kt:92-96); ModelSelectionDto = providerID/modelID pair (ModelStateDto.kt:6-9). CLI GET /provider already returns full catalogs for ALL enabled (incl. non-connected) providers in `all` (opencode handlers/provider.ts:54-107); JetBrains maps them verbatim (KiloWorkspaceDtoMapper.kt:46-50, KiloBackendWorkspace.kt:277) — filtering to kilo+connected is client-side only (ModelItems.kt:15, SessionSelection.kt:84).
2. UI: PickerPopup is a single flat list with search, section headers, favorite star, details panel, toolbar slot — no built-in two-level nav (PickerPopup.kt:60,67,181-190); a provider-first step would live in ModelPicker (owns rows closure, ModelPicker.kt:215) but Mode.Single auto-closes on pick (PickerPopup.kt:277-285). ModelPicker embeds in PromptPanel (PromptPanel.kt:149-153, bound at SessionUi.kt:708-724), Models settings (ModelsSettingsUi.kt), and NewWorktreeDialog (NewWorktreeDialog.kt:271-285). ProvidersSettingsUi has full connect/OAuth/disconnect/enable/custom-provider flows (ProvidersSettingsUi.kt:129-141).
3. STATE: selectModel persists only session-local prefs (SessionController.kt:871-888, pref fields :208) and reaches the CLI per-turn via PromptDto (KiloSessionRpcApi.kt:109). The ONLY persisted model selection is per-agent, machine-global `~/.local/state/kilo/model.json` (KiloBackendModelStateManager.kt:25,66-73) — written only by the New Worktree dialog path (KiloAppService.kt:287-298); same file the CLI reads/writes (kilocode/config/model-state.ts:51, provider.ts:2151). Agent/mode persists in PropertiesComponent (KiloPluginSettings.kt:21-24).
4. BOUNDARY: no CLI change needed to make non-connected provider models selectable (catalog already in `all`; restrictions are ModelItems.kt:15/SessionController.kt:1226-1228). A provider-level default would need changes in kilocode/config/model-state.ts (schema), cloud/defaults.ts, config-console.ts, provider.ts defaultModel, plus JetBrains mirrors ModelStateDto.kt, KiloCliDataParser.kt:868-894, KiloBackendModelStateManager.kt. Full endpoint tables in doc §4.
5. PARITY: TUI and VS Code both use flat grouped lists (Favorites/Recommended/provider sections), providerID/modelID selection unit, no provider-first drill-down (tui/src/kilocode/model-picker.ts:97-186; kilo-vscode ModelSelector.tsx:320-392). Flagged: ProvidersDto.defaults is provider-keyed on the wire but read agent-keyed in SessionSelection.kt:38-41 — likely dead fallback branch.
---

author: wayfinder
created: 2026-10-06 11:12
---
Findings doc relocated from `.kilo/plans/wayfinder/kilo-7.1-selection-surface.md` to Backlog document **doc-1** (`wayfinder/doc-1 — KILO-7.1 — Provider/model selection surface (JetBrains plugin)`). Loose-file findings are discontinued — rule: findings/plans/specs live on the board (AGENTS.md → Issue tracker).
---
<!-- COMMENTS:END -->
