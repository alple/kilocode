---
id: KILO-8.1
title: 'Plan the implementation: seed build tickets from the decided design'
status: Testing
assignee:
  - '@dev1'
created_date: '2026-10-06 12:33'
updated_date: '2026-10-06 13:05'
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

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Child build tickets exist on KILO-8 (CLI-first order honored) with native blocking dependencies wired
- [ ] #2 Each ticket cites the decision(s) it implements and names its touch surfaces
- [ ] #3 Hard constraints carried into every ticket: fork strategy, no ModelStateDto/model.json change
- [ ] #4 Favorites disposition and route transport mechanism decided and recorded (ticket texts + ADR-0002 clarification)
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-06 13:05
---
Resolution (2026-10-06, HITL grilling): seeded **3 build tickets** under KILO-8 (user chose consolidation over the 6-ticket draft shape):

1. [CLI: route support — endpoints proxy + per-turn route transport](KILO-8.2) — Kilo-owned API group + handler proxying OpenRouter `/models/{author}/{slug}/endpoints` (dedupe by `tag`, attach stored OpenRouter key when available else public); additive per-turn `route` field on `PromptInput` + kilo-owned resolve helper (`provider: {order: [tag], allow_fallbacks: false}` merged at highest precedence in `request.ts`); SDK regen. No deps.
2. [JetBrains: per-project parameter store + session selection wiring](KILO-8.3) — project-level `PersistentStateComponent` (workspace.xml, `ai.kilocode.client.deluxe`): model per agent, effort+route per (provider, model), mode/agent moved out of `KiloPluginSettings`; model.json selection/variant/route/mode writes stop (favorites keep the shared path); project tier on the 5-tier chain; dead `ProvidersDto.defaults` branch deleted; forced `firstOrNull` variant dropped (Auto when never picked); `syncModelSelection` re-fires narrowed; per-turn route+effort send incl. retries + `PromptDto`/`buildPromptJson` mirror. No deps (parallel with 8.2).
3. [JetBrains: Deluxe parameter component — picker UI, routing option, staleness](KILO-8.4) — fork-marked package `ai.kilocode.client.deluxe`; model/route/effort rows + lock chip; `PickerPopup` shell with fork-owned renderer/rows; backend bridge for the route list; "enable model routing" option (provider-id list defaulting `openrouter`+`openrouter-private`); staleness keep-pin + notice + refreshed list. Depends on 8.2 + 8.3.

Session decisions (grilling rounds): route transport needs an additive per-turn field (evidence: variant rides `PromptDto.variant` → `PromptInput.variant` → `input.model.variants[key]` merge — no channel exists for a routing pin; config paths are static; writing picks to CLI config is forbidden by the SessionController rule) → folded into the CLI ticket per user's fewer-tickets preference; **favorites keep the shared model.json write path**; fork package root = **`ai.kilocode.client.deluxe`**; proxy auth = **key-when-available else public**. ADR-0002 got the one-line favorites clarification. All six KILO-7 decisions + both ADRs cited across the three tickets; hard constraints carried in every ticket.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Seeded the KILO-8 implementation epic from the decided KILO-7 design. Three consolidated build tickets created (user chose fewer, layer-shaped tickets over the 6-ticket draft): KILO-8.2 CLI route support (endpoints proxy + per-turn route transport, SDK regen), KILO-8.3 JetBrains project parameter store + session selection wiring (store, tier chain, dead-branch delete, Auto effort, narrowed re-resolution, per-turn send), KILO-8.4 JetBrains Deluxe parameter component (fork package `ai.kilocode.client.deluxe`, picker UI + lock, route-list bridge, routing option, staleness) — dependencies wired 8.4 → {8.2, 8.3}, CLI-first honored. Planning-time decisions made with the user: per-turn route transport is an additive PromptDto/PromptInput field resolved by a kilo-owned CLI helper; favorites keep the shared model.json write path (ADR-0002 clarified); fork package root `ai.kilocode.client.deluxe`; route proxy auth = stored key when available, public otherwise. Each ticket cites its decisions and names file:line touch surfaces; hard constraints (fork strategy, no ModelStateDto/model.json change) carried throughout.
<!-- SECTION:FINAL_SUMMARY:END -->
