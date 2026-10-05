---
id: KILO-5.1
title: >-
  Close KILO-5 rewind test gaps: rollback settle in SessionUi and queued
  publication
status: To Do
assignee: []
created_date: '2026-10-05 13:46'
labels: []
milestone: Chat rewind
dependencies: []
documentation:
  - packages/kilo-jetbrains/AGENTS.md
  - >-
    packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionUi.kt
  - >-
    packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/MessageView.kt
  - >-
    packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/actions/SessionContextMenuActionsTest.kt
parent_task_id: KILO-5
type: task
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
KILO-5 shipped rewind-to-any-message with tests covering the controller, panel toolbars, banner, and context-menu action. Two small behaviors are only indirectly exercised (see KILO-5 final summary "Known gaps" items 1-2); a third (split-mode) needs a manual sandbox run that Gradle tests cannot cover per packages/kilo-jetbrains/AGENTS.md.

Scope:
1. **SessionUi rollback settle** — `SessionUi.onRevertChanged` clears `pendingRollback` and calls `scroll.followBottom(true)` for *any* resulting revert marker now (the CLI widens an assistant/tool boundary to the preceding user message, so the marker id can differ from the clicked message). No test pins this: cover via `SessionUiTestBase`/`realDataManager()` — drive the click through the published `SessionActions.rollback` on the ACTIONS key (SessionUi.revert is private), emit a widened `SessionRevertDto` marker, and assert the pending rollback settles and the scroll view follows bottom; then a failure path (revert stays null, e.g. error state) asserts no pending state lingers.
2. **Queued publication through the real chain** — `MessageView.uiDataSnapshot` publishes `SessionMessageRef(id, queued)`; only `queued=false` for history messages is asserted today. Add a `SessionUiTestBase`-based test: send a prompt while busy so the user message becomes queued (`model.isQueued`), then through `DataManager.getDataContext` assert the ref carries `queued=true` and that `MessageRollbackAction.update` hides the item for that target.
3. **Split-mode sandbox check (manual)** — run `runIdeSplitMode`, roll back an assistant and a tool-result message in a session with tool calls, verify no `LinkageError`/serializer errors from shared DTOs over RPC and that the restore banner appears. Record the run in the ticket on completion.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SessionUi-level test asserts that clicking rollback on an assistant message settles pendingRollback when the CLI marker widens to the preceding user message (banner scroll follows), and that a failing revert (no marker) leaves no pending rollback behind
- [ ] #2 SessionUi-level test asserts a revert marker arriving with no pending rollback is a no-op: no followBottom call, no state change
- [ ] #3 MessageView UiDataProvider test through the real DataManager chain asserts queued=true is published after setQueued(true) and the rollback menu item hides for that target
- [ ] #4 Split-mode sandbox check (manual, checklist in ticket): rollback on an assistant and a tool-result message over real frontend-backend RPC reports no serializer/linkage errors and the banner shows
<!-- AC:END -->
