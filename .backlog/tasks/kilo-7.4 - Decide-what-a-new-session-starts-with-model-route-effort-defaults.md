---
id: KILO-7.4
title: Decide what a new session starts with (model/route/effort defaults)
status: To Do
assignee: []
created_date: '2026-10-06 09:13'
labels:
  - 'wayfinder:grilling'
dependencies:
  - KILO-7.3
parent_task_id: KILO-7
priority: medium
type: enhancement
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

Once the shape decision (KILO-7.3) settles the selection unit: what does a **new** session start with — model, route, and reasoning effort?

- Inheritance source: last-used (`model.json` recent), per-agent defaults, or a single global default. KILO-7.1 found the only persisted selection today is per-agent `model.json` written solely by the New Worktree dialog, and `ProvidersDto.defaults` is a dead fallback branch (provider-keyed on the wire, read agent-keyed in `session/controller/SessionSelection.kt:38-41`) — verify/fix or build on it.
- Whether route and effort participate in defaults or always start unset (Auto / provider default).
- Interplay with the freeze: a new session starts unlocked; is the inherited value shown as editable, and does the freeze lock the inherited value at first message?

HITL grilling session with the user; the decision lands as a resolution comment naming the inheritance rule and its contract implications (`ModelStateDto`, `model.json` schema).
<!-- SECTION:DESCRIPTION:END -->
