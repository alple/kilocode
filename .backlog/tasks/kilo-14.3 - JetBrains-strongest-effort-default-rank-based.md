---
id: KILO-14.3
title: 'JetBrains: strongest-effort default (rank-based)'
status: To Do
assignee:
  - dev1
created_date: '2026-10-13 18:57'
updated_date: '2026-10-13 18:57'
labels: []
dependencies: []
parent_task_id: KILO-14
priority: medium
ordinal: 40000
---

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Never-picked effort resolves to the strongest variant by rank: max > xhigh > high > medium > low > none; unranked names lose to any ranked one
- [ ] #2 Applies in SessionController.selectResolvedModel and the New Worktree dialog's effort display default
- [ ] #3 A stored effort that is no longer offered by the model falls through to the rank-based default, not to firstOrNull
- [ ] #4 Agent-config variant override still wins over the default; gradlew typecheck + tests green
<!-- AC:END -->
