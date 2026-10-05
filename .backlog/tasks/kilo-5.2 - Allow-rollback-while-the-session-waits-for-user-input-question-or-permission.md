---
id: KILO-5.2
title: Allow rollback while the session waits for user input (question or permission)
status: Testing
assignee: []
created_date: '2026-10-05 15:57'
updated_date: '2026-10-05 16:20'
labels: []
dependencies: []
modified_files:
  - >-
    packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/model/SessionState.kt
  - >-
    packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/controller/SessionController.kt
  - >-
    packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionUi.kt
  - >-
    packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionActions.kt
  - >-
    packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/SessionMessageListPanel.kt
  - >-
    packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/ui/RevertBanner.kt
  - >-
    packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/actions/MessageRollbackAction.kt
  - >-
    packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/controller/SessionRewindWaitingTest.kt
  - >-
    packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/controller/TurnLifecycleTest.kt
  - >-
    packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/controller/SessionCancellationTest.kt
  - >-
    packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/session/ui/SessionMessageListPanelTest.kt
  - >-
    packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/actions/SessionContextMenuActionsTest.kt
parent_task_id: KILO-5
type: enhancement
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Revises the KILO-5 "idle-only rewind" decision (AC #3, gate decided with the developer 2026-10-05): while the turn is paused waiting for the user — the `ask`/question tool pending (AwaitingQuestion) or a permission prompt pending (AwaitingPermission) — streaming has stopped, yet rollback affordances are hidden because both states count as busy (`SessionState.isBusy()`). The user hit exactly this: the session sat on the ask tool's question UI and no rollback popup, context-menu item, or hover button was reachable.

New semantics: "waiting for the user" is rewindable; "working" is not.

- `AwaitingQuestion` and `AwaitingPermission` count as rewindable (rollback hover buttons enabled, context-menu item visible, banner redo enabled). `Busy`, `Reverting`, `Retry`, `Offline` still refuse.
- Because the CLI's own `assertNotBusy` guard still blocks revert while a turn runner exists, a rollback in a waiting state must settle the pending interaction first: reject the pending question (`rejectQuestion` exists) / deny the pending permission / abort the turn, wait for the state to settle (reusing the KILO-5.1 settle machinery), then run the revert. No CLI changes (KILO-5 constraint).
- Consequence recorded: the pending ask/permission is rejected as part of the rollback; its turn content is hidden by the revert boundary anyway.
- Developer approved this revision 2026-10-05 (both waiting states included, per in-session grilling).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 #1 While the session state is AwaitingQuestion or AwaitingPermission, rollback hover buttons are enabled, the right-click "Rollback to this message" item is visible, and RevertBanner redo actions are enabled.
- [x] #2 #2 While the state is Busy, Reverting, Retry, or Offline, all rollback affordances stay hidden/disabled as today.
- [x] #3 #3 Triggering rollback in a waiting state first rejects/denies the pending question or permission (or aborts the turn), waits for the settle, then performs the revert; no CLI code changes.
- [ ] #4 #4 Spot-check: rollback while a question is pending leaves no dangling question/permission request and the next prompt continues from the boundary without part errors.
- [x] #5 #5 JetBrains typecheck and tests pass; controller/panel/action tests cover the waiting-state visibility and the settle-then-revert flow.
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Rollback now works while the turn waits for you (pending question or permission), and still refuses while actively streaming.

- `SessionState` gained `isWaiting()` (AwaitingQuestion/AwaitingPermission) and `isRewindable()` (= not busy, or waiting) as the single rewind predicate; `SessionUi.rewindable`, `SessionMessageListPanel.rewindable()`, and `RevertBanner.setReverting` all read it now.
- `SessionController.revert/redoTo/unrevert` capture the pending interaction on the EDT *before* `beginReverting` swaps the state to Reverting (initial bug caught by the new tests), then a `settle()` coroutine step rejects the pending question (`rejectQuestion`) or denies the permission (`PermissionReplyDto("reject")`), aborts the resumed turn, and only then calls revert — mirroring the pre-KILO-5 abort-then-revert flow, restricted to waiting states. `stopRequested` marks the settle abort so its error never reads as an unexpected cancellation. No CLI changes.
- Tests: new `SessionRewindWaitingTest` (5: question settle, permission deny, redo settle, unrevert settle, no-error-after-settle-abort), plus real-DataManager context-menu test (`rollback stays visible while the turn waits for input`), panel test (rollback button enabled in both waiting states, disabled while busy), banner test (redo/all enabled in waiting states). Updated stale "idle-only" doc comments in `MessageRollbackAction`, `SessionActions`, panel, banner.
- Checks: `./gradlew typecheck` green; full `:frontend:test` 4383 tests — only 2 failures, both in `TitleButtonTest` (pixel centering), verified pre-existing by running it against HEAD with my changes stashed aside (identical 2 failures); all affected suites green (controller/panel/context-menu/rollback/permission/recovery = 283 tests, 0 failures).
- Remaining: AC#4 CLI-side spot-check (no dangling pending request after a settle-rollback, next prompt continues from the boundary) needs a manual `runIdeSplitMode` run — the fake API can't prove CLI-side cleanup.
<!-- SECTION:FINAL_SUMMARY:END -->
