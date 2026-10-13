---
id: KILO-15
title: >-
  JetBrains: wire the selection chain to the project store; drop model.json
  tiers
status: To Do
assignee:
  - dev1
created_date: '2026-10-13 18:56'
labels: []
dependencies: []
priority: high
ordinal: 39000
---

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Resolution chain reads: session pin → project store → per-agent config → global config → fallback; no model.json tier
- [ ] #2 selectModel/selectVariant (composer/pending state) and the New Worktree dialog write the project store, not model.json
- [ ] #3 Opened sessions with a resolved selection are never re-resolved by background events; blank/unresolved sessions complete bootstrap exactly once
- [ ] #4 selectResolvedModel no longer falls back to variants.firstOrNull() (replaced in ticket 14.3; here it resolves to no default)
- [ ] #5 Remembered agent moves from KiloPluginSettings (IDE-global) to the project store; gradlew typecheck + tests green
<!-- AC:END -->
