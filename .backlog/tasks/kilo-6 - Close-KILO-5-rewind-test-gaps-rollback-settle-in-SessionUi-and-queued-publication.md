---
id: KILO-6
title: >-
  Close KILO-5 rewind test gaps: rollback settle in SessionUi and queued
  publication
status: To Do
assignee: []
created_date: '2026-10-05 13:34'
labels: []
milestone: Chat rewind
dependencies: []
references:
  - KILO-5
documentation:
  - packages/kilo-jetbrains/AGENTS.md
  - >-
    packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/SessionUi.kt
  - >-
    packages/kilo-jetbrains/frontend/src/main/kotlin/ai/kilocode/client/session/views/MessageView.kt
  - >-
    packages/kilo-jetbrains/frontend/src/test/kotlin/ai/kilocode/client/actions/SessionContextMenuActionsTest.kt
type: task
ordinal: 6000
---

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SessionUi-level test asserts that clicking rollback on an assistant message settles pendingRollback when the CLI marker widens to the preceding user message (banner scroll follows), and that a failing revert (no marker) leaves no pending rollback behind
- [ ] #2 SessionUi-level test asserts pendingRollback is left in place when a revert marker for a different session flow arrives while the click is pending, and no stale scroll happens
- [ ] #3 MessageView UiDataProvider test through the real DataManager chain asserts queued=true is published after setQueued(true) and the rollback menu item hides for that target
- [ ] #4 Split-mode sandbox check (manual, checklist in ticket): rollback on an assistant and a tool-result message over real frontend-backend RPC reports no serializer/linkage errors and the banner shows
<!-- AC:END -->
