---
id: KILO-7.3
title: 'Decide the picker shape: extend, duplicate, or new provider picker'
status: To Do
assignee: []
created_date: '2026-10-06 07:27'
labels:
  - 'wayfinder:grilling'
dependencies:
  - KILO-7.2
parent_task_id: KILO-7
priority: high
type: enhancement
ordinal: 12000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

Given the settled intent (KILO-7.2) and the mapped surface (KILO-7.1): which implementation shape wins for the JetBrains plugin?

- **Extend** the existing `ModelPicker` popup (sections/level-two navigation inside it).
- **Duplicate** the picker and extend the copy.
- **New separate provider-level picker** alongside it.

Constraint framing for this decision (already established): `packages/kilo-jetbrains/` is entirely Kilo-owned — there is no opencode upstream to conflict with, so the TUI fork-conflict calculus does not transfer. The real trade-offs are shared-code churn (`ModelPicker` is reused by models settings + New Worktree dialog + session UI), the RPC DTO contract with the CLI, and cross-surface consistency. The decision records the shape, why the losers lost, and any contract changes the shape implies.

HITL grilling session with the user.
<!-- SECTION:DESCRIPTION:END -->
