---
id: KILO-9
title: >-
  JetBrains: favorites management — named model, route and effort presets with
  labels
status: To Do
assignee: []
created_date: '2026-10-09 09:27'
updated_date: '2026-10-09 12:27'
labels:
  - 'wayfinder:map'
dependencies: []
priority: high
type: epic
ordinal: 27000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Destination

A decided design (ADR + spec) for favorites management in the JetBrains plugin: a dedicated management window where a favorite is a **named combination of model + OpenRouter route + reasoning effort**, carrying user-assigned **labels**, where the labels decide which favorites the session parameter picker shows; the session popup surface (restored or cloned old popup) displays the labeled favorites. Done = design settled and build tickets seeded.

## Notes

Domain: JetBrains plugin chat session header. Legacy favorites are flat `(provider, model)` pairs surfaced by the app (`app.favorites` / `toggleModelFavorite`, `ModelSelectionDto`). The Deluxe parameter picker (KILO-8.4, ADR-0001) composes model + route + effort in the session header, with the upstream `ModelPicker` popup still bound but hidden since the header swap.

Constraints and conventions to respect: fork-owned Deluxe surfaces in `ai.kilocode.client.deluxe` per ADR-0001 (upstream `ModelPicker`/`PickerPopup` internals untouched for new features; session-header-scale upstream edits only); per-project parameter store (ADR-0002) is the JetBrains write target; no new shared provider-state DTO fields; Swing + IntelliJ platform components only (no Kotlin UI DSL v2, Compose, or JCEF); user-visible strings in bundle properties. Route data comes from the CLI `/kilo/routes/{author}/{slug}` proxy (uptime/pricing/quantization per endpoint).

Skills: grilling + domain-modeling for decision tickets; jetbrains-ui / jetbrains-session / jetbrains-arch for UI and architecture grounding.

## Decisions so far

- [Design the favorites management window and the labeled-favorites functionality](KILO-9.1): favorites become named route presets (model + route + effort + labels) in an IDE-global store; unlabeled = hidden; a label matching the project name scopes a preset into that project's popup; session popup = Deluxe clone of the old popup (Favorites presets + catalog + hover sidecar, no route/effort sections); management window = non-modal Data-Sources-style dialog with label accordion, drag gestures, per-label ordering, RPG-style price deltas; flat favorites untouched. Decided in ADR-0003.

## Not yet specified

Label rename/delete management UI (v1 = free-type assignment only); live-refresh of an already-open popup while the window edits presets; single-window behavior across several simultaneously open projects; preset schema migration story beyond store sanitize.

## Out of scope

- Benchmark/leaderboard data beyond existing model metadata (terminal-bench etc.) — needs new data plumbing, not in v1. (from KILO-9.1 design pass)

## Spec — favorites as labeled route presets (ADR-0003)

**Data model.** A favorite is a route preset: `{id, unique name, labels[], provider, modelId, routeTag? (null = Auto), variant? (null = Auto effort)}`. No other params in v1. One preset, N labels — no per-project copies; edits propagate everywhere; running conversations keep their pinned values (ADR-0002 on-demand re-resolution contract). Stored app-level (`PersistentStateComponent`, config-dir storage file, default roaming) — **global and surviving IntelliJ version updates**; JetBrains-only, CLI/`model.json` untouched. Names unique with auto-suffix on conflict; sanitize on load.

**Labels.** Free-form tags auto-created as typed. Unlabeled presets visible in no project popup. Preset shows in project P's popup iff a label equals P's name (case-insensitive).

**Session popup** — Deluxe clone of the current Kilo Code popup experience, in `ai.kilocode.client.deluxe` (cloned shell `PickerPopup`/`PickerListRenderer`, rows/renderer, details panel; upstream untouched; today's `KiloParameterPicker` popup superseded):
- Rows: Favorites (one row per visible preset: name, provider · model · route · effort) → full catalog; hover sidecar shows the model's details, preset rows add the preset's route/effort block.
- Favorites section header always visible with the edit-favorites button on its right; muted empty-state row when the project has no presets.
- Route/effort sections leave the popup; the three diagnosed parameter-picker defects (route below the fold, invisible Loading note, scroll yank) die with them. Plain model pick inherits store route/effort.
- Lock: Unlock row on top while frozen; preset picks refused while frozen; a picked preset applies model+route+effort as one through the project store.
- Star = per-model toggle of "visible preset exists here": star creates a preset named after the model with the currently-in-effect route/effort, labeled with the project's name (no-op when one already exists); unstar removes the project label from the model's visible presets. Flat favorites untouched (TUI + upstream pickers keep them); Ctrl+2 cycles visible presets, falling back to flat favorites.

**Management window** — non-modal DialogWrapper styled after IntelliJ's Data Sources and Drivers dialog, OK/Cancel/Apply, never blocking other windows/popups; singleton opened with the invoking project's tab in view; entry points: the popup Favorites-line button and Find Action.
- Left pane: accordion grouped by label (auto groups + unlabeled); collapsed groups auto-expand under the drag cursor; drag row → group adds that label at the drop position; drag within a group reorders that label's sequence — the order that project's popup uses; per-view "order alphabetically" button; +/−/duplicate toolbar (duplicate = the only independent fork, "name copy", no labels); free-text filter over name/label/model.
- Right editor: name; label chips with autocomplete; provider/model combos filtered to routing-enabled providers (the "enable model routing" option gates the catalog; option off or no such provider ⇒ explanatory empty state); route list from the CLI `/kilo/routes` proxy (Auto first; tag, provider, quantization, uptime, pricing; sortable/filterable by price/uptime; cheapest endpoint highlighted); effort combo from the model's variants (Auto = none); stale route/effort flagged per keep-pin, never auto-cleared.
- Details block: input/output/cached/average price per 1M tokens, context, terminal-bench, capabilities, plus the RPG-style delta vs the preset currently selected in the window list — blended-average headline (cache 0.7·read + 0.2·in + 0.1·out, else 0.9·in + 0.1·out) with per-row input/output/cached deltas; green = cheaper, red = pricier, neutral when either side lacks prices.

**Expectation:** this is a first cut — field testing follows implementation, and modification tickets will be added from what the user sees.
<!-- SECTION:DESCRIPTION:END -->
