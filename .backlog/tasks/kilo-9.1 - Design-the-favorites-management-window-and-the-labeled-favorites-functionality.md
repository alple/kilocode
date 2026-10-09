---
id: KILO-9.1
title: Design the favorites management window and the labeled-favorites functionality
status: In Progress
assignee:
  - dev1
created_date: '2026-10-09 09:27'
updated_date: '2026-10-09 11:15'
labels:
  - 'wayfinder:grilling'
dependencies: []
parent_task_id: KILO-9
priority: high
type: task
ordinal: 29000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why

The session parameter picker (KILO-8.4) shows a flat model list with route/effort sections buried below the fold, and favorites are flat `(provider, model)` pairs with no memory of route or effort. The vision: restore or clone the old popup, and add a completely new window to manage favorites — finding the best model/route/effort combination, naming it, assigning labels, and those labels then determine which favorites the session picker shows.

## Decisions to settle while designing (the fog)

The vision is deliberately loose; these are settled as part of the design work, not guessed:

1. **Which "old popup"?** Two candidates: the upstream `ModelPicker` popup (still wired for Ctrl shortcuts but hidden from the header since the KILO-8.4 binding swap) and the Deluxe `KiloParameterPicker` popup (ADR-0001). Resurrect the upstream picker as the favorites display, clone it into a Deluxe-owned surface, or keep the parameter picker and layer favorites around it — ADR-0001's churn-avoidance constraints apply.
2. **What is a favorite?** Today: flat `(provider, model)` pairs via `app.favorites` / `toggleModelFavorite` (`ModelSelectionDto`). Proposed: a named preset of model + route + effort. Replace the flat favorites, extend them, or run both with a migration?
3. **Labels**: vocabulary model (user-created tags), storage (per-project parameter store vs app-level IDE state vs CLI-visible config), scope (project vs global), and whether presets need any CLI visibility.
4. **How do labels drive the picker?** Which favorites show in the session popup — manual filter selection, context-based auto-filtering, a default label, or something else?
5. **"Finding the best model/route/effort combination"**: what the management window offers — route endpoint data (uptime/pricing/quantization from the CLI `/kilo/routes` proxy), benchmark scores, or plain manual composition.
6. **The management window's shape**: tool window, modal dialog, or settings page; entry point; module placement under the Deluxe package conventions.

## Deliverables

ADR + spec covering the management window and the labeled-favorites functionality (composing a preset with route data in view, naming, label management, label-driven session-picker filtering, fate of the existing flat favorites), then seeded build tickets as children of KILO-9.

## Design inputs from the route-picker session

- Route section renders after the entire model catalog in `kiloParameterRows`, so it sits below the fold with the popup capped at ~10 visible rows.
- The route fetch sets a Loading state but only repaints — the section does not appear in the popup model until the fetch callback lands.
- On fetch completion the popup refresh prefers the model row key and scrolls back to it, yanking the user's scroll position.
- The route data path itself is healthy end-to-end: CLI `/kilo/routes/{author}/{slug}` proxies OpenRouter endpoints live (verified HTTP 200 for `z-ai/glm-5.3-flash`).

## References

- ADR-0001 (Deluxe-owned route picker) and ADR-0002 (per-project parameter store): conventions and constraints.
- KILO-7.5 (OpenRouter endpoints survey): route data available to the window.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each fog question (popup surface, favorite data model, labels storage and scoping, label-driven filtering, route/effort selection aid, window shape) has a written decision with rationale and rejected alternatives recorded in the ADR or a resolution comment
- [ ] #2 An ADR in docs/adr/ records the decided design: favorites data model (named model+route+effort presets with labels), storage location, the management window shape and entry point, and the popup surface that displays labeled favorites
- [ ] #3 A spec (epic description update or Backlog document) details the functionality enough to seed build tickets: composing a preset from model, route and effort with route data in view; naming; assigning and managing labels; how the session picker filters favorites by labels; and what happens to the existing flat favorites
- [ ] #4 The diagnosed parameter-picker popup defects (route section below the fold, missing loading state on first open, scroll reset when the route fetch completes) are each addressed by the design or explicitly ruled in or out with rationale
- [ ] #5 The design follows ADR-0001 conventions: Deluxe-owned surfaces in the fork-marked package, minimal upstream edits, no new shared provider-state DTO fields, Swing + platform components only
- [ ] #6 Any question still unresolved after the design pass becomes a new child ticket of KILO-9 or is recorded under the map's Not-yet-specified section
- [ ] #7 Build tickets are seeded from the spec as children of KILO-9 with dependency edges, or the follow-up that seeds them is recorded on the map
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
"Grilling session started 2026-10-09. Design inputs from user: popup keeps models + sidecar details pane; edit-favorites button on Favorites top line opens a new non-modal window modeled on IntelliJ's Data Sources and Drivers dialog; window is OpenRouter-compatible-API-only; favorites = global named presets (name + labels + provider/model/route/effort params); project name is a label that scopes the preset into that project's popup; details pane shows pricing and RPG-style green/red price comparison vs the current model."
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-09 09:45
---
Merged from the fog-clearing ticket that originally carried the KILO-9.1 id (archived, then removed when this ticket reclaimed the id at the developer's direction). Its six fog questions live verbatim in this ticket's "Decisions to settle while designing" section; the design work settles them — no separate fog pass. Numbering history: fog ticket was created as KILO-9.1, this ticket as KILO-9.2; after the merge this ticket is KILO-9.1 and the next child of KILO-9 takes the next number.
---
<!-- COMMENTS:END -->
