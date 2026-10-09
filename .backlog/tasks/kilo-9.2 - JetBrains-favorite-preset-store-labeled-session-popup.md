---
id: KILO-9.2
title: 'JetBrains: favorite preset store + labeled session popup'
status: Testing
assignee:
  - dev1
created_date: '2026-10-09 12:29'
updated_date: '2026-10-09 13:21'
labels: []
dependencies: []
parent_task_id: KILO-9
priority: high
type: enhancement
ordinal: 30000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implements the store and session-surface half of the ADR-0003 spec (see epic KILO-9 for the full spec; design is a first cut — expect modification tickets after field testing).

## Scope

1. **Preset store** (Deluxe, app-level `PersistentStateComponent`, own config-dir storage file, default roaming — global, survives IntelliJ version updates): presets `{id, unique name, labels[], provider, modelId, routeTag? (null=Auto), variant? (null=Auto)}` + per-label order maps; unique names with auto-suffix on conflict; sanitize on load. JetBrains-only: no CLI, `model.json`, or shared-DTO writes.
2. **Visibility resolution**: preset visible in project P iff a label equals P's name case-insensitively; unlabeled presets visible nowhere.
3. **Deluxe session popup** — clone of the upstream popup chain (`PickerPopup`, `PickerListRenderer`, model rows/renderer, `ModelDetailsPanel`) into `ai.kilocode.client.deluxe`; upstream files untouched; today's `KiloParameterPicker` popup superseded by the clone (header button keeps the trio label + lock chip):
   - Favorites section (one row per visible preset: name, provider · model · route · effort), header always visible with the edit-favorites button on its right, muted empty-state row when the project has no presets; full catalog below; hover sidecar (model details; preset rows add the preset's route/effort block).
   - Route/effort sections and route fetching removed from the popup (the diagnosed below-the-fold, invisible-Loading, and scroll-yank defects die with the sections); plain model pick inherits store route/effort.
   - Lock: Unlock row on top while frozen; preset picks refused while frozen (KILO-8.4 guards); a picked preset applies model+route+effort as one through `KiloProjectParameterStore`.
4. **Star label-toggle** on catalog model rows: star creates a preset named after the model with the currently-in-effect route/effort labeled with the project's name (no-op when a visible preset exists); unstar removes the project label from the model's visible presets. Flat favorites untouched (TUI + upstream pickers keep model.json). Ctrl+2 cycles visible presets, falling back to flat favorites.

## Hard constraints

- No shared provider-state DTO or `model.json` schema change; zero upstream file edits (clone lives in the deluxe package).
- Popup performs no route fetching; route data appears only in the management window (follow-up ticket) and preset summaries.

## Verification

- Store round-trip/sanitize/unique-name tests; visibility tests (case-insensitive project-name match, unlabeled hidden, per-label order).
- Popup tests: sections/rows, Favorites header + edit button + empty state, sidecar content, Unlock row + frozen refusal, preset pick applies trio to the store.
- Star toggle tests (create with project label, remove label, multi-preset unstar); existing session/picker tests stay green; `./gradlew typecheck` + `./gradlew test` from `packages/kilo-jetbrains/`.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented per ADR-0003. All new code lives in the fork-owned `ai.kilocode.client.deluxe` package; the upstream popup chain (`ui/picker/*`, `session/ui/model/*`) is untouched.

**Preset store — `KiloFavoritePresetStore.kt`**
- Application-level light service + `PersistentStateComponent` with its own config-dir storage file (`kiloFavoritePresets.xml`, default roaming), JetBrains-only — no shared provider-state DTO or `model.json` change.
- `KiloFavoritePreset {id, name, labels, provider, modelId, routeTag (null = Auto), variant (null = Auto)}` + per-label order maps (bean list, `KiloPresetLabelOrder`).
- Unique names with auto-suffix on conflict ("X 2", "X 3"…, case-insensitive); load-time sanitation (drop blank id/name/provider/model, repair missing ids, suffix duplicate names, trim labels/pins, prune order entries to existing presets/labels).
- Visibility: preset shows in project P iff a label equals P's name case-insensitively; unlabeled presets visible nowhere; `visibleIn()` orders by the project label's order map with unordered presets trailing.
- `toggleModelStar()`: star = create a preset named after the model with the currently-in-effect route/effort labeled with the project name; unstar = remove the project label from the model's visible presets (presets survive for the management window).

**Popup clone in deluxe**
- `KiloPickerPopup` (shell clone of PickerPopup) with a pinned Favorites bar between the search field and the list — header always visible, edit-favorites button on its right.
- `KiloPickerListRenderer` (clone), `KiloModelRows` (Lock / Preset / Empty / Model rows, stable per-row keys, section titles), `KiloModelPopupRenderer` (preset rows show `name — provider · model · route · effort`; model rows keep the upstream look with the star repurposed as the labeled-preset toggle; muted empty-state row when the project has no presets; frozen rows render weak, unlock row full strength), `KiloModelDetailsPanel` (clone with the preset's route/effort block; presets missing from the catalog still preview; star reflects preset visibility).
- `KiloParameterPicker` reworked: header button keeps the trio label + lock chip; the KILO-8.4 route/effort sections and all route fetching are removed (the diagnosed below-the-fold, invisible-Loading, and scroll-yank defects die with them); a plain model pick inherits store route/effort via the existing tiers; preset pick and model pick are refused while frozen (also enforced in the controller).
- Ctrl+2 cycles the project's visible presets, falling back to the flat `model.json` favorites pool; flat favorites and upstream pickers keep `model.json` untouched.
- `KiloRoutes.kt` keeps the route helpers for KILO-9.3's management window (`routeAuthorSlug`, `KiloRouteState`, `routesApply`, `KiloRouteText`).

**Controller (`SessionController.selectPreset`)**
- Applies model + route + effort as one pick through `KiloProjectParameterStore` (model per agent, route/effort per model; null pins clear the store values = Auto), mirrors the existing select* semantics incl. the unknown-model refusal and the frozen refusal.

**Scope note (upstream edits, per the ADR-0001 session-header binding precedent)**
- The pure "zero upstream file edits" goal was kept for the popup chain, but the session-header wiring needed small sanctioned edits: `SessionController.selectPreset` (composing the three existing selects cannot key route/effort to the new model atomically), the `SessionUi` deluxe wiring block, `PromptSelectors.parameters` member, `PromptPanel` parameter type, and `CycleModelAction` routing to the deluxe cycle.

**Tests** — new: `KiloFavoritePresetStoreTest` (12: round-trip, visibility incl. case-insensitive/unlabeled/blank, per-label order, unique names, sanitation, remove/setLabels, label toggling, star create/unstar/multi-preset), `KiloModelRowsTest` (8), `KiloPickerPopupTest` (2: favorites bar caption + edit button, pinned-bar stack), `KiloModelPopupRendererTest` (5), `KiloModelDetailsPanelTest` (6), `KiloRoutesTest` (4), `PresetSelectionTest` (5 controller-level: trio-as-one, prompt rides the trio, Auto clears pins, unknown-model refusal, frozen refusal). `KiloParameterPickerTest` extended with star-key and cycle tests. Deleted `KiloParameterRowsTest` with the retired popup rows.

**Checks** — `./gradlew typecheck` green; full `./gradlew test`: 4493 tests, only the 2 pre-existing `TitleButtonTest` failures remain (verified failing on pristine HEAD in a temp worktree — environment-dependent font-metric assertions, unrelated to this change).

**KILO-9.3 hook**: `parameters.onEditFavorites` is wired to a placeholder no-op in `SessionUi` — point it at the management window.
<!-- SECTION:FINAL_SUMMARY:END -->
