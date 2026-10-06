---
id: KILO-7.5
title: Survey OpenRouter per-model server list API and routing preferences
status: To Do
assignee: []
created_date: '2026-10-06 09:13'
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
