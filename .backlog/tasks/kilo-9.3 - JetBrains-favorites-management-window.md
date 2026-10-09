---
id: KILO-9.3
title: 'JetBrains: favorites management window'
status: Testing
assignee: []
created_date: '2026-10-09 12:31'
updated_date: '2026-10-09 15:09'
labels: []
milestone: 'JetBrains: favorites management'
dependencies:
  - KILO-9.2
parent_task_id: KILO-9
priority: high
type: enhancement
ordinal: 31000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implements the management-window half of the ADR-0003 spec (see epic KILO-9 for the full spec; design is a first cut — expect modification tickets after field testing).

## Scope

1. **Window shell**: non-modal `DialogWrapper` styled after IntelliJ's Data Sources and Drivers dialog — master list left, editor right, OK/Cancel/Apply at the bottom; never blocks other IntelliJ windows/popups. Singleton instance, opened with the invoking project's context (its label preselected); entry points: the popup Favorites-line button (from KILO-9.2) and Find Action.
2. **Left pane**: accordion grouped by label (auto groups + an unlabeled group); collapsed groups auto-expand under the drag cursor; drag row → group adds that label at the drop position; drag within a group reorders that label's sequence (drives that project's popup order); per-view "order alphabetically" button; +/−/duplicate toolbar (duplicate = the only independent fork: "name copy", no labels); free-text filter over name/label/model.
3. **Right editor**: name (unique, auto-suffix on conflict); label chips with autocomplete of existing labels; provider/model combos filtered to routing-enabled providers (the "enable model routing" option gates the catalog; option off or no such provider ⇒ explanatory empty state); route list from the CLI `/kilo/routes` proxy (Auto first; tag, provider name, quantization, uptime, pricing per 1M; sortable/filterable by price and uptime; cheapest endpoint highlighted); effort combo from the model's variants (Auto = none); stale route/effort flagged per keep-pin, never auto-cleared.
4. **Details block**: input/output/cached/average price per 1M tokens, context, release, terminal-bench, capabilities; RPG-style delta vs the preset currently selected in the window list — blended-average headline (cache 0.7·read + 0.2·in + 0.1·out, else 0.9·in + 0.1·out) with per-row input/output/cached deltas; green = cheaper, red = pricier, neutral when either side lacks prices.

## Hard constraints

- Swing + IntelliJ platform components only (no Kotlin UI DSL v2, Compose, JCEF); theme-derived colors/fonts/insets; user-visible strings in bundle properties; all Swing on EDT, RPC off EDT (`@RequiresEdt` discipline).
- Presets edited in the window are live-linked (no per-project copies); running sessions unaffected (ADR-0002 contract).
- No shared provider-state DTO or `model.json` changes; zero upstream file edits.

## Verification

- Window state tests (isModified/apply/cancel write-through to the preset store); drag semantics tests (add-label-at-position, per-label reorder, auto-expand); per-label ordering reflected in visibility resolution; gate empty-state tests; price-delta computation tests (cheaper/pricier/neutral); route sort/filter tests; `./gradlew typecheck` + `./gradlew test` from `packages/kilo-jetbrains/`.
<!-- SECTION:DESCRIPTION:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-09 15:09
---
**Implementation summary (KILO-9.3 → Testing)** — merged upstream `main` first (556 commits, one conflict resolved in `packages/kilo-jetbrains/gradle.properties`), then implemented on top in the fork-owned `deluxe` package. Zero shared-opencode or provider-DTO edits; the only KILO-9.2 file touched is `KiloFavoritePresetStore` (+`edit()` for renames/field updates, matching the existing API style) and the sanctioned one-line `SessionUi` placeholder wiring for `onEditFavorites` (KILO-9.2's spec sanctioned this exact call site for the popup Favorites-line button entry point). All new code lives in `ai.kilocode.client.deluxe` + one action class + bundle keys + one XML action registration + a changeset (`jetbrains-favorites-management-window.md`).
---
<!-- COMMENTS:END -->
