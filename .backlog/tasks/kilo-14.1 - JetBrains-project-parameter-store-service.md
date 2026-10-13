---
id: KILO-14.1
title: 'JetBrains: project parameter store service'
status: To Do
assignee:
  - dev1
created_date: '2026-10-13 18:56'
updated_date: '2026-10-13 19:07'
labels: []
dependencies: []
parent_task_id: KILO-14
priority: high
type: enhancement
ordinal: 38000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Slice

First slice of KILO-14 (the route map's step 1): the per-project persistent store, nothing wired yet.

## Settled design (from the epic — reiterate)

- New project-level light service in the frontend module, Deluxe-owned package (new `ai.kilocode.client.deluxe`): `@Service(Service.Level.PROJECT)` + `@State(name = "KiloParameterStore", storages = [Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)])`, implementing `PersistentStateComponent<State>`. Annotation-registered only — no plugin.xml `<projectService>` entry (matches plugin convention; verified by the previous attempt's research).
- State (copy-on-write, null-free maps): `model: Map<String, String>` (agent → `"provider/model"`), `variant: Map<String, String>` (`"provider/model"` → effort name), `agent: String?` (remembered agent/mode).
- API: `model(agent)`, `setModel(agent, provider, modelId)`, `clearModel(agent)`, `variant(key)`, `setVariant(key, value)`, `clearVariant(key)`, `agent()`, `setAgent(name)`.
- Machine-local (`RoamingType.DISABLED`) — picks do not sync across machines.

## Known gaps / open questions for the implementing session

- Is `model per agent` + `variant per (provider, model)` the right keying, or should effort also be per agent? (The previous attempt used per-model effort; the session-level pin covers per-session differences. Recommend keeping per-model effort.)
- Remembered agent: `KiloPluginSettings` (`kilo.session.agent`) currently holds it IDE-global — the cross-project leak for the agent switcher. Moving it into this store is in scope here (write side only; the wiring ticket moves the reads).

## Definition of done

- Store round-trips through `getState`/`loadState` (plain JUnit test, direct instantiation — `KiloLogSettingsServiceTest` precedent).
- `./gradlew typecheck` + frontend tests green.

**Parent route:** KILO-14, step 1 (**Next**).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Store service round-trips state via getState/loadState in a plain JUnit test; maps copy-on-write, null-free; workspace.xml (RoamingType.DISABLED)
- [ ] #2 Resolution chain reads: session pin → project store → per-agent config → global config → fallback; no model.json tier
- [ ] #3 selectModel/selectVariant (composer/pending state) and the New Worktree dialog write the project store, not model.json; the model.json write path is deleted
- [ ] #4 Opened sessions with a resolved selection are never re-resolved by background events; blank/unresolved sessions complete bootstrap exactly once
- [ ] #5 Remembered agent moves from KiloPluginSettings (IDE-global) to the project store; gradlew typecheck + tests green
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-13 19:07
---
Restructured 2026-10-13: former steps 1+2 merged into this ticket (developer: the change is simple and contained — two tickets total). This ticket now covers BOTH the store and the wiring. Merged design from the former step-2 ticket: (1) Resolution chain becomes session pin → project store → per-agent config default → global config default → fallback; the model.json tiers disappear from reads entirely (hard cut, no migration — existing global picks ignored, each project re-picks once). (2) Writes: selectModel/selectVariant while the session is still pending (composer state before the first message) and the New Worktree dialog's picks go to the project store; clearModelOverride clears the agent's stored pick. (3) Stickiness contract: a session that already has a resolved selection is never re-resolved by background events (catalog reload, config patch, model-state refresh, another session's pick); blank/unresolved sessions complete their bootstrap from the store exactly once; variant-list validity may refresh, values may not. (4) SessionController gains the store as an explicit constructor dependency; KiloPluginSettings' IDE-global remembered agent (kilo.session.agent) moves into the store (reads AND writes). (5) selectResolvedModel's forced variants.firstOrNull() tail is dropped here (never-picked → no default; the successor ticket adds the strongest-variant default on top). Open questions for the implementing session: write-through question (do established-session picks also update the store, or only pending/composer picks? Recommendation: pending/composer + New Worktree picks write the store; established-session picks stay session-local; a 'make this the project default' affordance would be a small follow-up, not write-through). Delete vs leave the now-unread KiloBackendModelStateManager selection/variant write methods — recommend deleting the write path while keeping ModelStateDto/favorites intact. Fallback tier when store and config are both empty — keep as-is (first picker item / provider default).
---
<!-- COMMENTS:END -->
