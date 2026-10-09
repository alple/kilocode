---
id: KILO-9.2
title: Design the favorites management window and the labeled-favorites functionality
status: Backlog
assignee: []
created_date: '2026-10-09 09:27'
labels:
  - 'wayfinder:grilling'
dependencies:
  - KILO-9.1
parent_task_id: KILO-9
priority: high
type: task
ordinal: 29000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Why

The session parameter picker (KILO-8.4) shows a flat model list with route/effort sections buried below the fold, and favorites are flat `(provider, model)` pairs with no memory of route or effort. The vision: restore or clone the old popup, and add a completely new window to manage favorites — finding the best model/route/effort combination, naming it, assigning labels, and those labels then determine which favorites the session picker shows.

## What this ticket produces

The decided design for the whole feature: the management window (shape, entry point, module placement) and its functionality (composing model + route + effort into a named preset, label management, label-driven filtering in the session popup), plus the fate of the old popup and of the existing flat favorites. Deliverables: ADR + spec, then seeded build tickets. Blocked until KILO-9.1 records the decisions it exists to make; this ticket must not re-litigate them.

## Design inputs from the route-picker session

- Route section renders after the entire model catalog in `kiloParameterRows`, so it sits below the fold with the popup capped at ~10 visible rows.
- The route fetch sets a Loading state but only repaints — the section does not appear in the popup model until the fetch callback lands.
- On fetch completion the popup refresh prefers the model row key and scrolls back to it, yanking the user's scroll position.
- The route data path itself is healthy end-to-end: CLI `/kilo/routes/{author}/{slug}` proxies OpenRouter endpoints live (verified HTTP 200 for `z-ai/glm-5.3-flash`).

## References

- KILO-9.1 (blocking): the decisions this design consumes.
- ADR-0001 (Deluxe-owned route picker) and ADR-0002 (per-project parameter store): conventions and constraints.
- KILO-7.5 (OpenRouter endpoints survey): route data available to the window.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An ADR in docs/adr/ records the decided design: favorites data model (named model+route+effort presets with labels), storage location, the management window shape and entry point, and the popup surface that displays labeled favorites
- [ ] #2 A spec (epic description update or Backlog document) details the functionality enough to seed build tickets: composing a preset from model, route and effort with route data in view; naming; assigning and managing labels; how the session picker filters favorites by labels; and what happens to the existing flat favorites
- [ ] #3 The diagnosed parameter-picker popup defects (route section below the fold, missing loading state on first open, scroll reset when the route fetch completes) are each addressed by the design or explicitly ruled in or out with rationale
- [ ] #4 The design follows ADR-0001 conventions: Deluxe-owned surfaces in the fork-marked package, minimal upstream edits, no new shared provider-state DTO fields, Swing + platform components only
- [ ] #5 Build tickets are seeded from the spec as children of KILO-9 with dependency edges, or the follow-up that seeds them is recorded on the map
<!-- AC:END -->
