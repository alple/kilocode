---
id: KILO-14.1
title: 'JetBrains: project parameter store service'
status: Backlog
assignee:
  - dev1
created_date: '2026-10-13 18:56'
labels: []
dependencies: []
parent_task_id: KILO-14
ordinal: 38000
---

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Store service round-trips state via getState/loadState in a plain JUnit test
- [ ] #2 Maps are copy-on-write and never hold null values
- [ ] #3 State lands in workspace.xml (RoamingType.DISABLED), no plugin.xml registration needed
- [ ] #4 gradlew typecheck and frontend tests pass
<!-- AC:END -->
