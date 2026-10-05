---
id: KILO-5.2
title: Allow rollback while the session waits for user input (question or permission)
status: To Do
assignee: []
created_date: '2026-10-05 15:57'
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
