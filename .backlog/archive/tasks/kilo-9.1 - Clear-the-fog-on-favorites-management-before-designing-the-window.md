---
id: KILO-9.1
title: Clear the fog on favorites management before designing the window
status: To Do
assignee: []
created_date: '2026-10-09 09:27'
updated_date: '2026-10-09 09:35'
labels:
  - 'wayfinder:grilling'
dependencies: []
parent_task_id: KILO-9
priority: high
type: spike
ordinal: 28000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

What exactly are we building, before any favorites design or spec is written? The vision (from the route-picker session): probably restore or clone the old popup, and add a completely new window to manage favorites — finding the best model/route/effort combination, naming it, assigning labels, and those labels then determine which favorites to show. The vision is deliberately loose; these decisions must not be guessed during design.

## The fog to clear

1. **Which "old popup"?** Two candidates: the upstream `ModelPicker` popup (still wired for Ctrl shortcuts but hidden from the header since the KILO-8.4 binding swap) and the Deluxe `KiloParameterPicker` popup (ADR-0001). Does "restore or clone" mean resurrect the upstream picker as the favorites display, clone it into a Deluxe-owned surface, or keep the parameter picker and layer favorites around it? ADR-0001's churn-avoidance constraints apply.
2. **What is a favorite?** Today: flat `(provider, model)` pairs via `app.favorites` / `toggleModelFavorite` (`ModelSelectionDto`). Proposed: a named preset of model + route + effort. Replace the flat favorites, extend them, or run both with a migration?
3. **Labels**: what is the vocabulary model (user-created tags), where are they stored (per-project parameter store vs app-level IDE state vs CLI-visible config), what scope do they have (project vs global), and do presets need any CLI visibility?
4. **How do labels drive the picker?** Which favorites show in the session popup — manual filter selection, context-based auto-filtering, a default label, or something else?
5. **"Finding the best model/route/effort combination"**: what does the management window actually offer — route endpoint data (uptime/pricing/quantization from the CLI `/kilo/routes` proxy), benchmark scores, or plain manual composition?
6. **The management window's shape**: tool window, modal dialog, or settings page; where it opens from; module placement under the Deluxe package conventions.

## Context

Spawned from the session that field-tested the parameter picker popup: the route section sits below the fold of a long model list, the loading state does not render until the route fetch lands, and the popup scrolls back to the model row when the fetch completes. Whatever popup survives becomes a design input, not a settled decision.

## Output

Decisions recorded per question with rationale (resolution comments), indexed into the KILO-9 map's Decisions-so-far; design ticket unblocked.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each open question (popup surface, favorite data model, labels storage and scoping, label-driven filtering, route/effort selection aid, window shape) has a written decision with rationale and rejected alternatives recorded in a resolution comment
- [ ] #2 The map KILO-9's Decisions-so-far section indexes every decision with a one-line gist and a link
- [ ] #3 Every question that stays unresolved after the pass becomes a new child ticket of KILO-9 or is recorded under its Not-yet-specified section
- [ ] #4 The answers give the design ticket enough to write the ADR and spec without re-litigating them
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-09 09:35
---
Merged into [KILO-9.2](KILO-9.2) — the design ticket now settles the fog questions as part of designing; no separate fog-clearing pass. This ticket is archived as superseded.
---
<!-- COMMENTS:END -->
