---
id: KILO-7.3
title: 'Decide the picker shape: extend, duplicate, or new provider picker'
status: Done
assignee:
  - dev1
created_date: '2026-10-06 07:27'
updated_date: '2026-10-06 13:48'
labels:
  - 'wayfinder:grilling'
dependencies:
  - KILO-7.2
parent_task_id: KILO-7
priority: high
type: enhancement
ordinal: 12000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

Given the settled intent (KILO-7.2: in-picker route choice + frozen session parameters), which implementation shape wins under the Deluxe fork strategy — **prefer Deluxe-owned implementations; stay away from Kilo Code UI; keep edits inside upstream UI files as small as possible**?

User lean (stated 2026-10-06): build a **Deluxe-owned parameter component** that injects model/route/effort, rather than leaning on the upstream `ModelPicker`/`PickerPopup`, because those will keep changing in Kilo Code and edits there conflict on every upstream sync. This ticket formalizes it:

- **Deluxe-owned parameter component** (new, fork-owned files) vs **minimal in-place edits** to upstream `ModelPicker`/`PickerPopup` vs **subclass/wrapper** around the upstream picker.
- Blast radius when touching upstream picker code (call sites from KILO-7.1): `PromptPanel` session header (`PromptPanel.kt:148-153`, bound `SessionUi.kt:705-724`), Models settings (`ModelsSettingsUi.kt` via `modelItems`), New Worktree dialog (`NewWorktreeDialog.kt:271-285`), AgentsConfigurable (`AgentsConfigurable.kt:413`), plus renderer/rows/tests. Each site's consequence per option must be listed.
- Where the Deluxe component lives (module: `frontend/`, package convention for fork-owned code) and how it reuses vs duplicates upstream pieces (`PickerPopup` is generic; `ModelPicker` is the upstream-specific layer).
- What contract changes the shape implies (route list DTO, freeze state).

Also settle: do route choices ride the existing `(providerID, modelID)` selection, or does the selection unit grow to `(providerID, modelID, route)` (affects `PromptDto`, `ModelSelectionDto`, `model.json` schema — CLI-side change named in KILO-7.1 §4)?

HITL grilling session with the user; ADR likely warranted (hard to reverse, real trade-offs, surprising without context).
<!-- SECTION:DESCRIPTION:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: wayfinder
created: 2026-10-06 11:02
---
Resolved. The decision:

1. **Shape: Deluxe-owned parameter component.** New fork-owned files own the model/route/effort/lock UI; the upstream `ModelPicker`/`PickerPopup`/rows/renderer stay untouched for routes. The only upstream edit is the session-header binding swap (`PromptPanel.kt:148-153` / `SessionUi.kt:705-724`, ~12-line scale — matching the rewind-feature precedent). Models settings, New Worktree dialog, and AgentsConfigurable keep the upstream picker unchanged.
2. **Location: fork-marked package directory** (e.g. `ai.kilocode.client.deluxe` or `client/session/ui/param`) — ownership visible, upstream syncs never touch it. First fork-marked package convention in this plugin.
3. **List UI: reuse upstream generic `PickerPopup`** as the popup shell (2 usages, stable); fork-owned renderer/rows for route data.
4. **Route transport: per-turn like variant** — rides the request like reasoning effort does (variant precedent: ReasoningPicker keyed provider/model; session-local state). No `ModelSelectionDto` / `model.json` schema change; the freeze stores route+effort in session-local state alongside the existing prefs.
5. ADR created: `docs/adr/0001-deluxe-owned-route-picker.md`.

Evidence: KILO-7.1 findings + call-site blast radius (all sites listed in the question); git delta vs upstream/main showing the rewind precedent (SessionUi.kt +12, SessionController.kt +82, same-package new files).
---
<!-- COMMENTS:END -->
