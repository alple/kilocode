---
id: KILO-8.1
title: 'Plan the implementation: seed build tickets from the decided design'
status: To Do
assignee: []
created_date: '2026-10-06 12:33'
labels: []
dependencies: []
parent_task_id: KILO-8
priority: high
type: task
ordinal: 21000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Work

Turn the decided design into sequenced, implementation-ready build tickets under the feature root [JetBrains plugin: implement model + route picking (OpenRouter and marked providers)](KILO-8).

Inputs to read first (all resolved): the six decision tickets on the map [JetBrains plugin: provider selection alongside model selection](KILO-7) — resolution comments carry the detail — plus [ADR-0001](docs/adr/0001-deluxe-owned-route-picker.md) and [ADR-0002](docs/adr/0002-per-project-parameter-store.md).

Expected ticket shape (adjust as planning dictates; wire native blocking dependencies):

1. **CLI route-list proxy endpoint + DTO** — proxies OpenRouter `GET /api/v1/models/{author}/{slug}/endpoints` (public, unpaginated; dedupe by `tag`); first in sequence, the picker depends on it (KILO-7.2/7.5). Watch the CLI-side contract rules (SDK regen if server routes change).
2. **Project parameter store** — Deluxe-owned project `PersistentStateComponent` (workspace.xml; `KiloLogSettingsService` precedent): model per agent, effort+route per (provider, model), mode/agent moved per-project out of `KiloPluginSettings`; JetBrains stops writing `model.json` (read-only legacy tier) (KILO-7.4, ADR-0002).
3. **Session selection wiring** — 5-tier chain + project tier on top; delete dead `ProvidersDto.defaults` pre-READY branch; drop forced first-variant tier (effort Auto when never picked); on-demand-only re-resolution (narrow `syncModelSelection` re-fires) (KILO-7.4).
4. **Deluxe parameter component** — fork-marked package; model/route/effort rows + lock UI; reuses generic `PickerPopup` with fork-owned renderer/rows; upstream edit limited to the session-header binding swap (~12 lines) (KILO-7.3, ADR-0001).
5. **Staleness handling** — route notice + refreshed route list presentation; keep pin until re-picked, unfreeze required when locked (KILO-7.4).
6. **"Enable model routing" option** — Deluxe-only option with configurable provider-id list (defaults `openrouter` + `openrouter-private`); OpenRouter-only v1 (KILO-7.6).

## Done when

- Child tickets exist on KILO-8 with dependencies wired, CLI-first order honored.
- Each ticket cites the decision(s) it implements and names its touch surfaces.
- Hard constraints carried: fork strategy, no `ModelStateDto`/`model.json` change.
<!-- SECTION:DESCRIPTION:END -->
