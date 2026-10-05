---
id: KILO-5.1
title: >-
  Close KILO-5 rewind test gaps: rollback settle in SessionUi and queued
  publication
status: Testing
assignee: []
created_date: '2026-10-05 13:46'
updated_date: '2026-10-05 14:43'
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
- [x] #1 SessionUi-level test asserts that clicking rollback on an assistant message settles pendingRollback when the CLI marker widens to the preceding user message (banner scroll follows), and that a failing revert (no marker) leaves no pending rollback behind
- [x] #2 SessionUi-level test asserts a revert marker arriving with no pending rollback is a no-op: no followBottom call, no state change
- [x] #3 MessageView UiDataProvider test through the real DataManager chain asserts queued=true is published after setQueued(true) and the rollback menu item hides for that target
- [ ] #4 Split-mode sandbox check (manual, checklist in ticket): rollback on an assistant and a tool-result message over real frontend-backend RPC reports no serializer/linkage errors and the banner shows
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added `frontend/src/test/kotlin/ai/kilocode/client/session/SessionUiRollbackTest.kt` (SessionUiTestBase, real DataManager, rollback driven through the published `SessionActions.rollback` via `MessageRollbackAction`) — 4 tests, all passing (`:frontend:test --tests ai.kilocode.client.session.SessionUiRollbackTest`; `./gradlew typecheck` green). No production code changed.

- AC#1: rollback click on an assistant message reaches the CLI as `a1` (`rpc.reverts`), then a widened `SessionRevertDto("u1")` marker settles the pending rollback: bar re-pins to bottom, `scroll.following()`, jump button hidden. Failure path: an `Error` event clears the pending rollback, and a later marker then neither scrolls nor hides the jump button.
- AC#2: a revert marker arriving with no pending rollback applies through the model only — bar value unchanged, not following, jump button stays visible.
- AC#3: prompt while busy + `SessionQueueChanged([u2])` publishes `SessionMessageRef(u2, queued=true)` through `DataManager.getDataContext`, and `MessageRollbackAction.update` hides the item; after `TurnClose` the settled prompt (queued=false) shows the item again while the queued target stays hidden, isolating the queued flag as the reason.

Remaining: AC#4 split-mode sandbox check (manual) — run `./gradlew --no-configuration-cache runIdeSplitMode`, roll back an assistant and a tool-result message in a session with tool calls, confirm no `LinkageError`/serializer errors over RPC and the restore banner appears. Checklist: 1) launch split-mode sandbox with pinned CLI; 2) open session with tool calls; 3) context-menu rollback on an assistant text message — expect widened user-message banner + follow-bottom; 4) context-menu rollback on a tool-result card; 5) redo once; 6) watch frontend+backend logs for `LinkageError`/serializer errors.
<!-- SECTION:FINAL_SUMMARY:END -->
