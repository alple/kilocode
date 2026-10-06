---
id: KILO-7.6
title: 'Decide route-support scope: OpenRouter-first or provider-agnostic'
status: Done
assignee: []
created_date: '2026-10-06 09:19'
updated_date: '2026-10-06 11:02'
labels:
  - 'wayfinder:grilling'
dependencies:
  - KILO-7.5
parent_task_id: KILO-7
priority: medium
type: enhancement
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

KILO-7.5 established OpenRouter's dedicated endpoints sub-resource (`/api/v1/models/{author}/{slug}/endpoints`, public, rich server data). Other multi-server providers have different or no server-list APIs. What is the v1 scope for the route picker?

- **OpenRouter-only v1**: provider-agnostic DTO shape, but only OpenRouter has a live-list adapter; other providers show no route step (config-only kilo.json pinning still available for them).
- **Survey other providers first**: add a research ticket for e.g. LLM Gateway / Together / Fireworks server-list APIs before deciding.
- **Generic config-only for all, live list for OpenRouter**: the route picker reads a normalized route list the CLI assembles — live from OpenRouter, static from config for others.

Weigh against: user's actual provider (OpenRouter), DTO stability, and the Deluxe strategy (CLI endpoint changes are shared-code surface — `packages/opencode` is upstream-synced).
<!-- SECTION:DESCRIPTION:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: wayfinder
created: 2026-10-06 11:02
---
Resolved. The decision:

1. **V1 scope: OpenRouter-only.** Only OpenRouter gets a live-list route adapter in v1; the DTO/flow is shaped to generalize later; other providers keep config-only kilo.json pinning (`options.provider.order`, verified working in KILO-7.5).
2. **Matching rule: NO new provider-state DTO field** (user: don't pollute/"break the protocol"). Instead a **Deluxe-branch-only option — "enable model routing"** — living in fork-owned code: when off (or absent), no route UI exists anywhere. The option carries a **configurable route-capable provider id list**, defaulting to `openrouter` and `openrouter-private` (the KILO-4 clone), user-extendable for custom-URL OpenRouter providers by adding their id. The client matches on that list; the CLI never has to declare protocol capability in shared state.
3. Consequence for the route-list fetch (decided live-via-CLI in KILO-7.2): the fetch surface remains to be named in implementation planning (additive RPC/HTTP), independent of this matching decision.

Evidence: KILO-7.5 findings (endpoints API public, tag slugs); KILO-4 description (openrouter-private clone shares npm/api, so id-list matching covers both); `Provider.Info.source` is provenance-only (env/config/custom/api), not protocol — hence client-side matching via the option list rather than server-declared capability.
---
<!-- COMMENTS:END -->
