---
id: KILO-7.2
title: Settle what "choosing a provider" means in the JetBrains model picker
status: Done
assignee:
  - dev1
created_date: '2026-10-06 07:27'
updated_date: '2026-10-06 09:13'
labels:
  - 'wayfinder:grilling'
dependencies:
  - KILO-7.1
parent_task_id: KILO-7
priority: high
type: enhancement
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

What does "choose the provider in addition to the model" mean for the user in the JetBrains picker — **settled: routing a given model to a chosen serving server inside its provider** (OpenRouter sub-providers like parasail, digitalocean for glm-5.3-flash). Remaining sub-decisions for this ticket:

- Confirm the intent excludes catalog-provider disambiguation (rows already carry that).
- Does the user prefer config-defined routes (kilo.json custom model entries, no code) or an in-picker route choice (needs CLI + JetBrains work), or config-first then UI?
- Persistence semantics: route as a default (per model/agent) vs per-session pick.

HITL grilling session with the user; the decision lands as a resolution comment naming the chosen intent and what it explicitly excludes.
<!-- SECTION:DESCRIPTION:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: wayfinder
created: 2026-10-06 09:13
---
Resolved. The decision:

1. **Intent: in-picker route choice.** For providers that serve one model from multiple servers (OpenRouter: parasail, digitalocean, …), the user picks model + serving server (+ reasoning effort) in the picker. Catalog-provider disambiguation (row labeling when the same model appears under multiple catalog providers) is explicitly excluded from the intent.
2. **Mechanism: in-picker route choice** — not config-only. The kilo.json `options.provider.order` path (verified pipeline in the map Notes) remains a working interim/fallback, but the decided design is the in-picker UX.
3. **Route data: live list via CLI.** The CLI proxies the provider's per-model server list (pricing/latency surfaced, an Auto option included); this needs a new CLI endpoint + DTO. API survey: KILO-7.5.
4. **Freeze semantics:** model + route + effort are editable until the session's **first message**; after that they lock **as one** (single lock for all three). Unfreezing is an explicit user action; any change applies **from the next message onward** — earlier turns keep the parameters they ran with.
5. **Persistence: sticky per session.** The frozen selection rides the session until the user unfreezes — not per-turn switching, not per-agent defaults. What a *new* session inherits is KILO-7.4's question (after the shape decision).

Evidence base: KILO-7.1 findings doc (`.kilo/plans/wayfinder/kilo-7.1-selection-surface.md`) + map Notes pipeline facts (request.ts:112 → transform.ts:1723; ProviderDto.kt:68-70; ModelPickerRenderer.kt:93).
---
<!-- COMMENTS:END -->
