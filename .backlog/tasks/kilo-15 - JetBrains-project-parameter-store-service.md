---
id: KILO-15
title: 'JetBrains: project parameter store service'
status: Backlog
assignee: []
created_date: '2026-10-13 18:56'
labels: []
dependencies: []
ordinal: 38000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Slice

First slice of KILO-14 (the route map's step 1): the per-project persistent store, nothing wired yet.

## Settled design (from the epic — reiterate)

- New project-level light service in the frontend module, Deluxe-owned package (new `ai.kilocode.client.deluxe`): `@Service(Service.Level.PROJECT)` + `@State(name = "KiloParameterStore", storages = [Storage(StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)])`, implementing `PersistentStateComponent<State>`. Annotation-registered only — no plugin.xml `<projectService>` entry (matches plugin convention; verified in the previous attempt's plan).
- State (copy-on-write, null-free maps): `model: Map<String, String>` (agent → `"provider/model"`), `variant: Map<String, String>` (`"provider/model"` → effort name), `agent: String?` (remembered agent/mode).
- API: `model(agent)`, `setModel(agent, provider, modelId)`, `clearModel(agent)`, `variant(key)`, `setVariant(key, value)`, `clearVariant(key)`, `agent()`, `setAgent(name)`.
- Machine-local (`RoamingType.DISABLED`) — picks do not sync across machines; nothing leaves the machine (anonymity rule: the store must never carry personal info anyway).

## Known gaps / open questions for the implementing session

- Is `model per agent` + `variant per (provider, model)` the right keying, or should effort also be per agent? (Previous attempt used per-model effort; the session-level pin covers per-session differences. Recommend keeping per-model effort.)
- Remembered agent: `KiloPluginSettings` (`kilo.session.agent`) currently holds it IDE-global — the cross-project leak for the agent switcher. Moving it into this store is in scope here (write side only; wiring ticket moves the reads).

## Definition of done

- Store round-trips through `getState`/`loadState` (plain JUnit test, direct instantiation — `KiloLogSettingsServiceTest` precedent).
- `./gradlew typecheck` + frontend tests green.

**Parent route:** KILO-14, step 1 (**Next**).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Store service round-trips state via getState/loadState in a plain JUnit test
- [ ] #2 Maps are copy-on-write and never hold null values
- [ ] #3 State lands in workspace.xml (RoamingType.DISABLED), no plugin.xml registration needed
- [ ] #4 gradlew typecheck and frontend tests pass
<!-- AC:END -->
