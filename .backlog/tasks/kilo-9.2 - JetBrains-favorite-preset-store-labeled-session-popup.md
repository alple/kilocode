---
id: KILO-9.2
title: 'JetBrains: favorite preset store + labeled session popup'
status: To Do
assignee: []
created_date: '2026-10-09 12:29'
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
