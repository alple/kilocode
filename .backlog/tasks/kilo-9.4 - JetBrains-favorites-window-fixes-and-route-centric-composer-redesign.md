---
id: KILO-9.4
title: 'JetBrains: favorites window fixes and route-centric composer redesign'
status: To Do
assignee: []
created_date: '2026-10-10 07:55'
labels:
  - ui
milestone: 'JetBrains: favorites management'
dependencies:
  - KILO-9.3
parent_task_id: KILO-9
priority: high
type: enhancement
ordinal: 32000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Field-testing KILO-9.3 surfaced two defects and a composition redesign for the favorites management window (ADR-0003).

## Bug 1 — no providers in the window's provider/model pickers

With OpenRouter configured, the favorites window shows no provider at all. Investigate and fix:

- The window fetches the catalog via `KiloWorkspaceService.models(directory)` (same call settings pages use) and filters providers through `KiloRoutingOptions.routesEnabled(provider.id)` (defaults: `openrouter`, `openrouter-private`). Check for a real id mismatch between the routing option list and the catalog's `ProviderDto.id` for a user-added OpenRouter, and for an empty/failed catalog payload.
- The catalog failure is swallowed silently: `KiloFavoritesEditor.ensureCatalog()` does `runCatching { catalog() }.getOrNull()` with no logging — an invisible failure. Log the actual error (`KiloLog`/`log.error`) and surface it in the gate message instead of a generic line.
- Verify in the split-mode sandbox (`runIdeSplitMode`) with an OpenRouter key configured, opened both from the popup button and Find Action.

## Bug 2 — the parameter popup stays open above the window

Opening the management window from the popup's Favorites-bar edit button leaves the popup covering the window. `KiloParameterPicker.favoritesBar()` (the edit `HoverIcon`, ~line 215) calls `onEditFavorites()` without dismissing the popup. Cancel the picker's popup (`popup?.cancel()`) before invoking the callback, and make sure the close doesn't put focus somewhere odd (the dialog should get focus).

## Redesign — route-centric composer

Replace the current right-editor composition area:

- **Top right**: name (where it is now), provider and model picks.
- **Middle/bottom-left**: the route list for the selected model, mirroring the session popup's route list — Auto first, live endpoints from the CLI `/kilo/routes` proxy, cheapest-endpoint highlight, price/uptime sort and filter.
- **Right**: a details sidecar for the *selected route*, showing everything the SDK reports about that route — pricing (prompt/completion per 1M, cache read/write, discount), uptime windows (5m/30m/1d), context length, max completion tokens, quantization, provider name, status/degraded flag.
- **No arbitrary values**: route, price and uptime must always bind to the live endpoint data of the chosen model — never placeholders or hand-rolled defaults; unknown/stale states are flagged, never fabricated.
- Keep: the left accordion, live-linked store write-through (OK/Apply/Cancel), the routing gate, staleness flags. Decide during implementation where the price-delta-vs-selected-preset display lands (sidecar or removed).
- Update ADR-0003's window-layout description if it no longer matches.

## Verification

- Sandbox check: provider/model pickers populated with OpenRouter configured; popup dismissed when the window opens; route sidecar shows live SDK values per selected route.
- `./gradlew typecheck` + `./gradlew test` from `packages/kilo-jetbrains/`; keep the KILO-9.3 tests green and add binding tests for the redesigned composer (route selection → sidecar content).
<!-- SECTION:DESCRIPTION:END -->
