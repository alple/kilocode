---
id: KILO-14.3
title: 'JetBrains: strongest-effort default (rank-based)'
status: To Do
assignee:
  - dev1
created_date: '2026-10-13 18:57'
updated_date: '2026-10-13 18:57'
labels: []
dependencies:
  - KILO-14.2
parent_task_id: KILO-14
priority: medium
ordinal: 40000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Slice

Third and last slice of KILO-14 (the route map's step 3): the "max instead of min" change itself.

## Settled design (from the epic — reiterate)

- When no effort was ever picked for a model (no session pin, nothing in the project store, no agent-config `variant`), the default is the **strongest variant by rank**: `max > xhigh > high > medium > low > none`.
- Unknown variant names are unranked and lose to any ranked one; if the model exposes only unranked names, fall back to the last listed entry (the previous attempt's convention) rather than the first.
- Applies in `SessionController.selectResolvedModel` (replacing the `variants.firstOrNull()` tail dropped in ticket 14.2) and in the New Worktree dialog's effort display default (its forced `firstOrNull()` becomes the rank-based default).
- A stored effort that the model no longer offers falls through to the rank-based default.
- Agent-config `variant` overrides still win over any default. Accepted consequence: max effort raises token cost per turn (developer-approved).

## Known gaps / open questions for the implementing session

- Should the picker visually distinguish "this is the auto default" from "you picked this"? Recommendation: no — keep it a plain preselected row this round.
- Rank order as a shared constant vs per-call list: one small helper, single definition.

## Definition of done

- Controller tests: strongest-by-rank across the real variant sets (low/high/max; none/low/medium/high/xhigh/max; minimal-only), stale-store fallthrough, config-override precedence.
- `./gradlew typecheck` + frontend tests green.

**Parent route:** KILO-14, step 3 (blocked by step 2).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Never-picked effort resolves to the strongest variant by rank: max > xhigh > high > medium > low > none; unranked names lose to any ranked one
- [ ] #2 Applies in SessionController.selectResolvedModel and the New Worktree dialog's effort display default
- [ ] #3 A stored effort that is no longer offered by the model falls through to the rank-based default, not to firstOrNull
- [ ] #4 Agent-config variant override still wins over the default; gradlew typecheck + tests green
<!-- AC:END -->
