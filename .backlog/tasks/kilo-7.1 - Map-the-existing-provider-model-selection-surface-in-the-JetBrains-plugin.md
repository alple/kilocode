---
id: KILO-7.1
title: Map the existing provider/model selection surface in the JetBrains plugin
status: In Progress
assignee:
  - dev1
created_date: '2026-10-06 07:27'
updated_date: '2026-10-06 07:27'
labels:
  - 'wayfinder:research'
dependencies: []
parent_task_id: KILO-7
priority: high
type: spike
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

What exactly exists today for provider+model selection in `packages/kilo-jetbrains/`, and what would any new provider-level choice need from the CLI contract?

Facts to establish (verified, with file/line evidence):

1. **Data**: what `ProvidersDto` / `ModelSelectionDto` / `ProviderSettingsDto` carry; the semantics of `cfg.connected`; whether disconnected providers' model catalogs are available to the frontend at all.
2. **UI**: how `ModelPicker` + `PickerPopup` + `ModelPickerRenderer` are built (sections, search, favorites, details panel, cycling via Ctrl+2); what `ProvidersSettingsUi` offers for connect/disconnect/auth; how the session header and New Worktree dialog embed pickers.
3. **State/persistence**: `SessionController.selectModel` — where the selection lands (model store, config `model` key?), per-session vs per-agent vs per-worktree.
4. **Contract boundary**: which parts of this flow call back into the CLI (RPC endpoints/DTOs) — i.e., what would need a CLI-side change if providers beyond `connected` (or provider-level defaults) must be selectable.
5. **Parity check**: how the CLI TUI and VS Code surfaces treat the same problem (grouping exists there too) — enough to judge consistency costs, not to design them.

Deliverable: a findings doc + the facts that the UX and shape decisions hang on.
<!-- SECTION:DESCRIPTION:END -->
