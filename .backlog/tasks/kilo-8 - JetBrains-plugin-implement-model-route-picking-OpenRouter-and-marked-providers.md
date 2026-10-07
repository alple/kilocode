---
id: KILO-8
title: >-
  JetBrains plugin: implement model + route picking (OpenRouter and marked
  providers)
status: Done
assignee: []
created_date: '2026-10-06 12:33'
updated_date: '2026-10-07 12:50'
labels: []
dependencies: []
priority: high
type: epic
ordinal: 20000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Goal

In the Kilo **JetBrains plugin**, the session header lets the user pick **model + route** for OpenRouter (and providers enabled by the Deluxe-only "enable model routing" option) plus reasoning effort; the parameter lock freezes model+route+effort as one at a session's first message; a new session inherits its parameters from the per-project parameter store.

## Spec — the decided design

The design is settled. Source of truth: the six resolved decision tickets on the map [JetBrains plugin: provider selection alongside model selection](KILO-7) and two ADRs:

- **[ADR-0001 — Deluxe-owned route picker with per-turn route transport](docs/adr/0001-deluxe-owned-route-picker.md)**: Deluxe-owned parameter component in a fork-marked package (first such convention in this plugin); upstream edit limited to the session-header binding swap (~12 lines, rewind precedent); list reuses generic `PickerPopup` with fork-owned renderer/rows; routes ride per-turn like reasoning variants.
- **[ADR-0002 — Per-project parameter store with on-demand-only re-resolution](docs/adr/0002-per-project-parameter-store.md)**: project-scoped `PersistentStateComponent` (workspace.xml) as the single JetBrains write target — model pick per agent, effort+route per (provider, model), mode/agent moved per-project out of IDE-global settings; JetBrains stops writing `model.json`; sessions re-resolve only on explicit user actions.
- **[Settle what "choosing a provider" means (KILO-7.2)](KILO-7.2)**: in-picker route choice; live route list proxied by the CLI (new endpoint + DTO); freeze at first message, explicit unfreeze, changes apply next message onward; sticky per session.
- **[Decide what a new session starts with (KILO-7.4)](KILO-7.4)**: model = 5-tier chain with the project tier on top; effort = inherited, Auto when never picked (forced first tier dropped); route = last-used, kept even when stale (notice + refreshed route list, re-pick on demand, unfreeze when locked); dead `ProvidersDto.defaults` pre-READY branch deleted; stability contract narrows `syncModelSelection` re-fires.
- **[OpenRouter endpoints survey (KILO-7.5)](KILO-7.5)**: `GET /api/v1/models/{author}/{slug}/endpoints` is public and unpaginated; pin one server via `provider: { order: ["<slug>"], allow_fallbacks: false }`; snake_case keys, slug values, untyped passthrough (kilo.json interim verified working).
- **[Route-support scope (KILO-7.6)](KILO-7.6)**: OpenRouter-only v1; matching via a Deluxe-only "enable model routing" option with a configurable provider-id list (defaults `openrouter` + `openrouter-private`); no new provider-state DTO fields.

## Hard constraints

- **Fork strategy**: Deluxe-owned files for all new surfaces; upstream edits minimal; the fork-marked package convention from ADR-0001.
- **No `ModelStateDto` / `model.json` schema change.**
- **CLI-first sequencing**: the CLI route-list proxy endpoint + DTO must land before the picker can show routes.
- kilo.json `options.provider.order` remains the working config-only interim (non-OpenRouter providers stay there).
<!-- SECTION:DESCRIPTION:END -->
