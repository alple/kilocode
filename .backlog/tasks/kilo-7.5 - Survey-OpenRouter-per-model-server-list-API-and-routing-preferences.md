---
id: KILO-7.5
title: Survey OpenRouter per-model server list API and routing preferences
status: Done
assignee:
  - dev1
created_date: '2026-10-06 09:13'
updated_date: '2026-10-06 13:48'
labels:
  - 'wayfinder:research'
dependencies: []
parent_task_id: KILO-7
priority: high
type: spike
ordinal: 14000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

What does OpenRouter expose for per-model **server (sub-provider) lists and routing**, such that the CLI can proxy a route list and the request pipeline can pin a server?

Facts to establish (public docs and/or live public API — no credentials needed):

1. **Endpoint(s)**: exact path(s) listing an OpenRouter model's serving endpoints/servers (e.g. `GET /api/v1/models/{author}/{slug}/endpoints` or equivalent), their auth requirement (public or key), and pagination/stability.
2. **Response shape**: per-endpoint fields — server/provider name, pricing (input/output, cache), context length, quantization, latency, uptime, supported parameters — with a real sample response for one popular multi-server model (e.g. a GLM or DeepSeek model).
3. **Routing preferences**: the exact semantics of the request-time `provider` preference object (`order`, `only`, `allow_fallbacks`, `sort`, `ignore`, `require_parameters`, `data_collection`) — how a client pins one server and disables fallback.
4. **CLI fit check**: with the pipeline facts in the map Notes (`kilocode/provider-options.ts` typing + `transform.ts:1723` namespacing), confirm how a pinned server preference would flow into an OpenRouter request — statically, no runtime call required.

Deliverable: findings doc + the facts the route-list DTO naming and Auto-option design hang on.
<!-- SECTION:DESCRIPTION:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: wayfinder
created: 2026-10-06 09:19
---
Resolved. Findings doc: `.kilo/plans/wayfinder/kilo-7.5-openrouter-endpoints.md` (live-verified against the public API, no credentials). Decisive facts for the route-picker design:

1. **Server list**: `GET /api/v1/models/{author}/{slug}/endpoints` — public (no auth, 200), no pagination, one array (34 endpoints for z-ai/glm-5.3-flash). The catalog `GET /api/v1/models` carries NO server data (`endpoint: null`) — the sub-resource is the only source. `GET /api/v1/providers` maps display names → slugs (Parasail → parasail).
2. **Endpoint fields**: name, provider_name, tag (routing slug incl. variant, e.g. `parasail/fp4`), context/max tokens, quantization, status (0 = healthy), uptime windows, latency/throughput percentiles (**null unless authed**), pricing + discount, supported_parameters, native_tools. Same tag can repeat (two baseten/fp8 rows) and endpoint sets churn (deepseek-r1 down to 1) — the picker/DTO must tolerate duplicates and churn.
3. **Pinning one server**: `provider: { order: ["<slug>/<variant>"], allow_fallbacks: false }` (base slug matches all variants; full tag pins exactly one; `only` equivalent). Setting `order`/`sort` disables load balancing.
4. **kilo.json interim works, two gotchas**: options flow config → request.ts:112 → transform.ts:1723 → SDK spreads `providerOptions.openrouter` verbatim into the body. Gotcha 1: `OpenRouterProviderOptions` has no `provider` key — untyped passthrough (works, invisible to TS). Gotcha 2: keys must be snake_case (`allow_fallbacks`) and `order` values must be slugs, not display names — map via endpoint `tag`.
5. Implication for the live route list: the CLI proxy endpoint can be thin (pass-through + slug/tag mapping), and latency/throughput fields need an authenticated call — an unauthenticated route list shows pricing/uptime/quantization but not latency percentiles.
---

author: wayfinder
created: 2026-10-06 11:12
---
Findings doc relocated from `.kilo/plans/wayfinder/kilo-7.5-openrouter-endpoints.md` to Backlog document **doc-2** (`wayfinder/doc-2 — KILO-7.5 — OpenRouter endpoints API and routing preferences survey`). Loose-file findings are discontinued — rule: findings/plans/specs live on the board (AGENTS.md → Issue tracker).
---
<!-- COMMENTS:END -->
