---
id: KILO-7.3
title: 'Decide the picker shape: extend, duplicate, or new provider picker'
status: In Progress
assignee:
  - dev1
created_date: '2026-10-06 07:27'
updated_date: '2026-10-06 09:19'
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
