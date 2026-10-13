---
id: KILO-14.2
title: >-
  JetBrains: wire the selection chain to the project store; drop model.json
  tiers
status: To Do
assignee:
  - dev1
created_date: '2026-10-13 18:56'
updated_date: '2026-10-13 18:57'
labels: []
dependencies:
  - KILO-14.1
parent_task_id: KILO-14
priority: high
ordinal: 39000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Slice

Second slice of KILO-14 (the route map's step 2): make the project store the single JetBrains memory for picks, and cut `model.json` out of the chain entirely.

## Settled design (from the epic — reiterate)

- **Resolution chain** becomes: session pin → **project store** → per-agent config default → global config default → fallback. The `model.json` tiers disappear from reads entirely (hard cut per the epic: no legacy override tier, no machine-global effort-map tier, no migration — existing global picks are ignored; each project re-picks once).
- **Writes**: `selectModel` / `selectVariant` while the session is still pending (composer state before the first message) and the New Worktree dialog's picks go to the project store. `clearModelOverride` clears the agent's stored pick.
- **Stickiness contract**: a session that already has a resolved selection is never re-resolved by background events (catalog reload, config patch, model-state refresh, another session's pick). Blank/unresolved sessions complete their bootstrap from the store exactly once. Variant-list validity may refresh; values may not.
- `SessionController` gains the store as an explicit constructor dependency; `KiloPluginSettings`' IDE-global remembered agent (`kilo.session.agent`) moves into the store (reads + writes).
- `selectResolvedModel`'s forced `variants.firstOrNull()` tail is dropped here (never-picked → no default; ticket 14.3 adds the strongest-variant default on top).

## Known gaps / open questions for the implementing session

- **Write-through question (the epic's open decision):** do established-session picks also update the store, or do only pending/composer picks write it? The literal reading of "stick to the session, inherited from the project" says session-local changes do NOT change the project default. Recommendation: pending/composer + New Worktree picks write the store; established-session picks stay session-local; if the user wants "make this the project default" from a session, that is a small follow-up affordance, not write-through.
- Delete vs leave the now-unread `KiloBackendModelStateManager` selection/variant write methods: recommend deleting the write path (the store replaces it) while keeping `ModelStateDto`/favorites intact.
- What the fallback tier should be when the store and config are both empty (today: first picker item / provider default) — keep as-is.

## Definition of done

- Controller/selection tests updated: store tier on top, no model.json read, background-event stability, bootstrap completion, write routing.
- `./gradlew typecheck` + frontend tests green.

**Parent route:** KILO-14, step 2 (blocked by step 1).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Resolution chain reads: session pin → project store → per-agent config → global config → fallback; no model.json tier
- [ ] #2 selectModel/selectVariant (composer/pending state) and the New Worktree dialog write the project store, not model.json
- [ ] #3 Opened sessions with a resolved selection are never re-resolved by background events; blank/unresolved sessions complete bootstrap exactly once
- [ ] #4 selectResolvedModel no longer falls back to variants.firstOrNull() (replaced in ticket 14.3; here it resolves to no default)
- [ ] #5 Remembered agent moves from KiloPluginSettings (IDE-global) to the project store; gradlew typecheck + tests green
<!-- AC:END -->
