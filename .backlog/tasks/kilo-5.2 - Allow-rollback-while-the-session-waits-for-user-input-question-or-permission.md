---
id: KILO-5.2
title: Allow rollback while the session waits for user input (question or permission)
status: In Progress
assignee: []
created_date: '2026-10-05 15:57'
updated_date: '2026-10-05 16:00'
labels: []
dependencies: []
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
- [ ] #1 #1 While the session state is AwaitingQuestion or AwaitingPermission, rollback hover buttons are enabled, the right-click "Rollback to this message" item is visible, and RevertBanner redo actions are enabled.
- [ ] #2 #2 While the state is Busy, Reverting, Retry, or Offline, all rollback affordances stay hidden/disabled as today.
- [ ] #3 #3 Triggering rollback in a waiting state first rejects/denies the pending question or permission (or aborts the turn), waits for the settle, then performs the revert; no CLI code changes.
- [ ] #4 #4 Spot-check: rollback while a question is pending leaves no dangling question/permission request and the next prompt continues from the boundary without part errors.
- [ ] #5 #5 JetBrains typecheck and tests pass; controller/panel/action tests cover the waiting-state visibility and the settle-then-revert flow.
<!-- AC:END -->
