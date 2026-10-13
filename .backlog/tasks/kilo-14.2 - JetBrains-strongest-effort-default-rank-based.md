---
id: KILO-14.2
title: 'JetBrains: strongest-effort default (rank-based)'
status: To Do
assignee:
  - dev1
created_date: '2026-10-13 19:07'
labels: []
dependencies:
  - KILO-14.1
parent_task_id: KILO-14
priority: medium
type: enhancement
ordinal: 39000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Slice

Second and last slice of KILO-14 (route map step 2): the max-instead-of-min change itself, on top of the store+wiring ticket.

## Settled design (from the epic - reiterate)

- When no effort was ever picked for a model (no session pin, nothing in the project store, no agent-config variant), the default is the strongest variant by rank: max > xhigh > high > medium > low > none.
- Unknown variant names are unranked and lose to any ranked one; if the model exposes only unranked names, fall back to the last listed entry rather than the first.
- Applies in SessionController.selectResolvedModel (replacing the variants.firstOrNull() tail dropped in kilo-14.1) and in the New Worktree dialog's effort display default (its forced firstOrNull() becomes the rank-based default).
- A stored effort that the model no longer offers falls through to the rank-based default.
- Agent-config variant overrides still win over any default. Accepted consequence: max effort raises token cost per turn (developer-approved).

## Known gaps / open questions for the implementing session

- Should the picker visually distinguish auto-default from user-picked? Recommendation: no - keep it a plain preselected row this round.
- Rank order as one shared helper with a single definition.

## Definition of done

- Controller tests: strongest-by-rank across the real variant sets (low/high/max; none/low/medium/high/xhigh/max; minimal-only), stale-store fallthrough, config-override precedence.
- ./gradlew typecheck + frontend tests green.

Parent route: KILO-14, step 2 (blocked by step 1, kilo-14.1).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Never-picked effort resolves to the strongest variant by rank: max > xhigh > high > medium > low > none; unranked names lose to any ranked one
- [ ] #2 Applies in SessionController.selectResolvedModel and the New Worktree dialog's effort display default
- [ ] #3 A stored effort that is no longer offered by the model falls through to the rank-based default, not to firstOrNull
- [ ] #4 Agent-config variant override still wins over the default; gradlew typecheck + tests green
<!-- AC:END -->
