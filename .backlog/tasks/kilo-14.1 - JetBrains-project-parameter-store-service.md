---
id: KILO-14.1
title: 'JetBrains: project parameter store service'
status: To Do
assignee:
  - dev1
created_date: '2026-10-13 18:56'
updated_date: '2026-10-13 19:06'
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
