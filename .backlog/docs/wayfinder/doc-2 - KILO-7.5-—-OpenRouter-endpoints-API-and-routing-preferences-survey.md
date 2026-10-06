---
id: doc-2
title: KILO-7.5 — OpenRouter endpoints API and routing preferences survey
type: other
created_date: '2026-10-06 11:10'
tags:
  - 'wayfinder:research'
  - openrouter
---
Ticket: KILO-7.5 · Date: 2026-10-06 · Type: research findings

# KILO-7.5 — Survey OpenRouter per-model server list API and routing preferences

Researched: 2026-10-06, against public OpenRouter docs and the live public API (all HTTP calls made
unauthenticated from this machine; response files captured under `/tmp/kilo/or-*.json`).
Scope: what a Kilo route-picker needs — (1) endpoints listing API, (2) response shape,
(3) request-time routing preferences, (4) how a pinned preference flows through Kilo's pipeline.

## 1. Endpoints API

### 1.1 `GET /api/v1/models/{author}/{slug}/endpoints` — verified working, no auth needed

- Exact path, OpenAPI `operationId: listEndpoints`, base server `https://openrouter.ai/api/v1`,
  path params `{author}` + `{slug}`: https://openrouter.ai/docs/api/api-reference/endpoints/list-all-endpoints-for-a-model.md
- **Empirically public**: real call with **no Authorization header** returned `HTTP 200`:
  `GET https://openrouter.ai/api/v1/models/z-ai/glm-5.3-flash/endpoints` → 200 with 34 endpoints
  (response captured; see §2). Same for `/api/v1/models/deepseek/deepseek-r1/endpoints` → 200 (1 endpoint).
- The OpenAPI spec nevertheless declares `security: apiKey` (bearer) globally and documents a 403
  "Only management keys can perform this operation" response. In practice the endpoint is
  publicly readable; the spec's security declaration is broader than observed behavior.
- Unknown model → `HTTP 404` `{"error":{"message":"Not Found","code":404}}` (real response for
  `/api/v1/models/z-ai/nonexistent/endpoints`).
- **Pagination: none.** The OpenAPI lists no query params beyond the path params; the response
  `data` object has exactly keys `id, name, created, description, architecture, endpoints`
  (`jq '.data | keys'`) — one full unpaginated list (34 endpoints for glm-5.3-flash).
  Note: model slugs resolve without their dated suffix (`glm-5.3-flash`, not `glm-5.3-flash-20260826`).

### 1.2 `GET /api/v1/models` — full catalog, NO per-endpoint data

- Verified: `HTTP 200`, unauthenticated, single unpaginated response (`links.next: null`,
  `total_count: 464` = `data.length: 464`). Doc page:
  https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties.md
- Per-model keys (verified): `architecture, benchmarks, canonical_slug, context_length, created,
  default_parameters, description, expiration_date, hugging_face_id, id, knowledge_cutoff, links,
  name, per_request_limits, pricing, reasoning, supported_parameters, supported_voices, top_provider`.
- There is an `endpoint` key per model but it was **`null` for every model** in the catalog
  (`jq '.data[] | select(.endpoint != null)'` → empty). The catalog does **not** carry
  per-endpoint/server data; the per-model `endpoints` sub-resource (§1.1) is the only server list.
- Catalog pricing is the canonical/top price, not the cheapest endpoint: `z-ai/glm-5.3-flash`
  catalog `pricing.prompt = "0.00000015"` matches Modal/Parasail endpoints, not the cheapest
  (Relace `0.000000036`). Endpoint-level pricing (§2) differs per server.

### 1.3 `GET /api/v1/providers` — provider slug registry (bonus, useful for a picker)

- Verified public, no auth: `HTTP 200`, entries `{name, slug, privacy_policy_url,
  terms_of_service_url, status_page_url, headquarters, datacenters}`. Mapping examples:
  `"Parasail" → slug "parasail"`, `"Z.AI" → slug "z-ai"` (real response).
  This is the registry that resolves display names → routing slugs.

### 1.4 Auth & rate limits on metadata endpoints

- No `X-RateLimit-*` headers on successful `GET /api/v1/models` or `/endpoints` calls (verified via
  response headers). OpenRouter docs: "Successful inference responses do not include
  `X-RateLimit-*` headers"; they appear only on `429` error responses.
  Source: https://openrouter.ai/docs/api_reference/limits.md
- Platform rate limits documented there are for *inference*: free-model variants (`:free`) get
  20 req/min and 50 req/day (<10 all-time credits) or 1000 req/day (≥10 credits); paid models have
  no platform request cap; Cloudflare DDoS protection otherwise. No published quota for the
  metadata endpoints; rapid successive calls in this survey were never throttled.
- Inference auth is `Authorization: Bearer <key>`; metadata endpoints accept unauthenticated GET.

## 2. Per-endpoint response shape (verified real sample)

From `GET /api/v1/models/z-ai/glm-5.3-flash/endpoints` (34 endpoints; trimmed real sample):

```json
{
  "name": "Parasail | z-ai/glm-5.3-flash-20260826",
  "provider_name": "Parasail",
  "tag": "parasail/fp4",
  "model_id": "z-ai/glm-5.3-flash",
  "model_name": "Z.ai: GLM 5.3 Flash",
  "context_length": 1048576,
  "max_completion_tokens": 943718,
  "max_prompt_tokens": null,
  "quantization": "fp4",
  "status": 0,
  "uptime_last_5m": null, "uptime_last_30m": 99.729, "uptime_last_1d": null,
  "latency_last_30m": null,
  "throughput_last_30m": null,
  "pricing": { "prompt": "0.00000015", "completion": "0.0000005",
               "input_cache_read": "0.00000003", "discount": 0 },
  "supported_parameters": ["reasoning","include_reasoning","max_tokens","temperature","top_p",
    "frequency_penalty","presence_penalty","repetition_penalty","seed","stop","top_k",
    "logit_bias","response_format","structured_outputs","tools","tool_choice","logprobs",
    "top_logprobs","reasoning_effort"],
  "supports_tool_choice": { "none": true, "auto": true, "required": true, "function": true },
  "supports_implicit_caching": true,
  "native_tools": {}, "supports_image_reference": false,
  "supports_multiple_audio_references": false, "supports_voice_cloning": false
}
```

Field semantics per the OpenAPI schema (`PublicEndpoint`, same URL as §1.1):

- `name` — display string `"Provider | <dated model slug>"`; `provider_name` — provider display
  name (enum with ~120 entries, e.g. "Parasail", "Z.AI", "Amazon Bedrock"); `tag` — **the provider
  routing slug incl. variant suffix** (e.g. `parasail/fp4`, `deepinfra/fp4`, `google-vertex/us-east5`,
  plain `openai`). `tag` is the value a picker should surface (see §4).
- `pricing` — string-denominated USD per token: `prompt`, `completion` (required), plus
  `input_cache_read`, `input_cache_write`, `input_cache_write_1h`, `request`, `image`, `audio`,
  `internal_reasoning`, `web_search`, `discount` (fraction), and conditional `overrides[]`
  (conditions: `min_prompt_tokens`, `utc_start`/`utc_end` HHMM windows, `utc_days` weekdays).
- `context_length`, `max_completion_tokens` (nullable), `max_prompt_tokens` (nullable).
- `quantization` — enum `int4, int8, fp4, mxfp4, nvfp4, fp6, fp8, mxfp8, fp16, bf16, fp32, unknown`.
- `status` — `EndpointStatus` enum `{0, -1, -2, -3, -5, -10}`; **0 = healthy**. The enum carries no
  descriptions in the spec; empirically negative values correlate with degraded availability
  (observed `-2` on Novita with 90.4% uptime_30m, `-5` on AtlasCloud with 53.1% uptime_30m vs 0 on
  ~99%+ endpoints). Semantics of each negative value are not publicly documented.
- `uptime_last_5m` / `uptime_last_30m` / `uptime_last_1d` — % success (rate-limited requests
  excluded; null if insufficient data).
- `latency_last_30m` / `throughput_last_30m` — `PercentileStats {p50,p75,p90,p99}` (latency ms = time
  to first token; throughput tok/s). **Auth-gated**: "Only visible when authenticated with an API key
  or cookie; returns null for unauthenticated requests" (OpenAPI field descriptions — matches the
  all-`null` values in our unauthenticated sample). Same for `perf_last_30m_by_workload`.
- `supported_parameters` — per-endpoint param enum (`temperature, top_p, top_k, min_p, top_a,
  frequency_penalty, presence_penalty, repetition_penalty, max_tokens, max_completion_tokens,
  logit_bias, logprobs, top_logprobs, prediction, seed, response_format, structured_outputs, stop,
  tools, tool_choice, parallel_tool_calls, include_reasoning, reasoning, reasoning_effort,
  web_search_options, verbosity`); `supports_tool_choice` breaks down which `tool_choice` variants
  actually passed testing (OpenAPI: "`tool_choice` in `supported_parameters` only says the parameter
  is accepted; these flags say which of its values passed testing").
- `native_tools` — server tools the endpoint runs natively, keyed by canonical `openrouter:*` name.
- Also observed: several endpoints can share one `tag` (two BaseTen rows both `baseten/fp8`) and one
  provider can host differently-quantized variants (Sail Research `sail-research/us`,
  `sail-research/fp4`) — so distinct endpoints > distinct slugs; dedupe by tag, rank by uptime/price.
- Endpoint churn is real: legacy `deepseek/deepseek-r1` now serves from a single endpoint
  (Novita `novita/fp8`) while glm-5.3-flash has 34. A picker must tolerate sparse lists.

## 3. Routing preferences — request-time `provider` object

Source page (all §3 facts): https://openrouter.ai/docs/features/provider-routing
(also linked as provider-selection guide: https://openrouter.ai/docs/guides/routing/provider-selection)

Request body `provider` object fields:

| Field | Type / default | Semantics |
|---|---|---|
| `order` | `string[]` | Provider **slugs** (not display names) tried in priority order; "OpenRouter will try them one at a time and proceed to other providers if none are operational". Setting `order` or `sort` disables the default price-based load balancing. |
| `allow_fallbacks` | `boolean`, default `true` | Whether backup providers are allowed when the primary is unavailable. |
| `only` | `string[]` | Restrict candidate providers to exactly this list for the request. |
| `ignore` | `string[]` | Skip listed provider slugs for this request. |
| `require_parameters` | `boolean`, default `false` | Only route to providers that support **all** request parameters (otherwise unknown params are silently dropped, and `tools`/`response_format`/`verbosity` are a soft routing preference). |
| `data_collection` | `"allow"\|"deny"`, default `"allow"` | `deny` excludes providers that may store/train on user data. |
| `quantizations` | `string[]` | Filter endpoints by quantization level (`["int4","int8"]` style; values match the §2 enum). |
| `sort` | `"price"\|"throughput"\|"latency"` or `{by, partition}` | Sort candidates; disables load balancing. `{partition: "none"}` sorts across models for fallback lists. |
| `zdr` | `boolean` | Only Zero-Data-Retention endpoints. |
| `enforce_distillable_text` | `boolean` | Only models whose author allows distillation. |
| `preferred_min_throughput` / `preferred_max_latency` | number or `{p50,p75,p90,p99}` | Deprioritize (not exclude) endpoints missing thresholds (rolling 5-min percentile stats). |
| `max_price` | object | Hard price cap; request fails rather than overpay. |

### Pinning ONE server

Docs example ("Targeting Specific Provider Endpoints", same URL): base slugs match all variants —
`"google-vertex"` matches `google-vertex`, `google-vertex/us-east5`, … (service-tier endpoints like
`openai/fast` are excluded from base-slug matching); full slug pins exactly one endpoint —
`"deepinfra/turbo"` matches only the turbo endpoint. Example given in docs:

```json
"provider": { "order": ["deepinfra/turbo"], "allow_fallbacks": false }
```

"So you can ensure your request is routed to the specific endpoint you want" — with
`allow_fallbacks: false`, the request "fails if Together fails" (disabling-fallbacks section).
The copy button on the model page "gives the exact provider slug, including any variants like
'/turbo'". Equivalent single-server pin without order: `"only": ["<slug>"]`.

## 4. Slug naming: display name vs routing slug (mismatch check)

- `provider_name` (display, e.g. `Parasail`, `Z.AI`, `Amazon Bedrock`) is **not** what routing fields
  expect. Routing (`order`/`only`/`ignore`/`quantizations`) wants **slugs**: lowercase, e.g.
  `parasail`, `z-ai`, `amazon-bedrock` (docs examples use lowercase slugs; `/api/v1/providers`
  registry confirms `Parasail → parasail`, `Z.AI → z-ai`).
- The endpoints API's `tag` field carries the **exact routing slug incl. variant** (`parasail/fp4`),
  i.e. the same string the docs' copy button yields. No other public field gives the full variant
  slug; `provider_name` alone cannot distinguish same-provider variant endpoints.
- For a picker: resolve a pinned server to `tag` (full slug) for one-endpoint pins, or `tag` minus
  the `/…` suffix for provider-wide pins; ignore `name` (display decoration).

## 5. CLI fit — how a pinned preference flows through Kilo today

Static trace (all repo refs from this worktree):

1. **kilo.json**: per-model options live at
   `provider.<id>.models.<model-id>.options` — schema `Model.options:
   Record(String, Any)` in `packages/core/src/v1/config/provider.ts`
   (ProviderConfig `models` record; per-provider `Info.options` is `StructWithRest` + catch-all).
2. **Merge**: `packages/opencode/src/provider/provider.ts:1613`
   `options: mergeDeep(existingModel?.options ?? {}, model.options ?? {})` — user config wins over
   the models.dev catalog entry.
3. **Request**: `packages/opencode/src/session/llm/request.ts:112`
   `mergeOptions(..., input.model.options, agentOptions, variant)` — model options land in the
   flat options bag (`provider: {...}` stays a top-level key).
4. **Namespacing**: `ProviderTransform.providerOptions` (`packages/opencode/src/provider/transform.ts:1723`)
   maps the bag under the SDK key: `sdkKey("@openrouter/ai-sdk-provider")` → `"openrouter"`
   (`transform.ts:95-96`; also `@kilocode/kilo-gateway` → `"openrouter"`). `anthropicBlockBinding`
   passes non-Claude options through untouched (`transform.ts:794` early return). Result:
   `providerOptions = { openrouter: { provider: { order: [...], allow_fallbacks: false } } }`.
   (transform.ts:1534-1544 additionally injects `usage: {include: true}` into that namespace.)
5. **Transport**: `@openrouter/ai-sdk-provider` spreads per-request options over the body —
   `doGenerate`/`doStream` both do `const { cacheControl, ...restOpenrouterOptions } =
   providerOptions.openrouter; args = { ...this.getArgs(options), ...restOpenrouterOptions }`
   (dist/index.js:3628-3631 and 3824-3826) — so `restOpenrouterOptions.provider` becomes the HTTP
   body's `provider` field verbatim. (The provider-level `settings.provider` also maps to
   `provider` in base args, per-request spread wins.)

### What a user writes today in kilo.json

```json
{
  "provider": {
    "openrouter": {
      "models": {
        "z-ai/glm-5.3-flash": {
          "options": {
            "provider": { "order": ["parasail/fp4"], "allow_fallbacks": false }
          }
        }
      }
    }
  }
}
```

### Fit-check verdict: WORKS, with two gotchas

- **Runtime flow works unmodified** — every hop is a passthrough; no code change needed to pin a
  server via config today (verified statically end-to-end above).
- **Gotcha 1 (typing)**: `OpenRouterProviderOptions` from `@openrouter/ai-sdk-provider`
  (dist/index.d.ts:381-396) declares only `models`, `reasoning`, `user` (+ shared settings
  `includeReasoning`, `extraBody`, `usage`, sampling opts); there is **no `provider` key** and no
  request-level zod schema — the spread is deliberately untyped passthrough. kilo.json accepts it
  (`options: Record(String, Any)`), TS consumers of the SDK type just won't see it. A route picker
  shipping `provider` values through model options needs no SDK changes, but if we type the surface
  ourselves we must not rely on `OpenRouterProviderOptions`.
- **Gotcha 2 (naming/casing)**: body keys are **snake_case** (`allow_fallbacks`, `require_parameters`);
  the official `@openrouter/sdk` TS examples show camelCase (`allowFallbacks`, `requireParameters`),
  but the AI-SDK-provider spread forwards keys **verbatim with no translation** — a config value
  `allowFallbacks` would reach OpenRouter as an unrecognized key inside `provider`. kilo.json must
  use snake_case. (Note `kiloProviderOptions` in `packages/opencode/src/kilocode/provider-options.ts`
  is only used for the kilo-gateway npm path and types OpenRouter options without routing fields —
  unrelated to the openrouter provider path above.)
- **Slug naming**: strings in `order` must be OpenRouter slugs (`parasail/fp4`), not the
  `provider_name`/display names (`Parasail`) the endpoints API also returns — a picker must map
  display → slug via the endpoints API `tag` field or the `/api/v1/providers` registry (§4).

## Sources

- OpenAPI + per-endpoint field docs: https://openrouter.ai/docs/api/api-reference/endpoints/list-all-endpoints-for-a-model.md (fetched 2026-10-06)
- Catalog: https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties.md
- Routing preferences: https://openrouter.ai/docs/features/provider-routing
- Limits/rate limits: https://openrouter.ai/docs/api_reference/limits.md
- Live calls (all 2026-10-06, unauthenticated): `/api/v1/models` (200, 464 models),
  `/api/v1/models/z-ai/glm-5.3-flash/endpoints` (200, 34 endpoints),
  `/api/v1/models/deepseek/deepseek-r1/endpoints` (200, 1 endpoint),
  `/api/v1/models/z-ai/nonexistent/endpoints` (404), `/api/v1/providers` (200).
  Captured under `/tmp/kilo/or-*.json` (ephemeral).
- Kilo pipeline: `packages/opencode/src/session/llm/request.ts:112`,
  `packages/opencode/src/provider/transform.ts:45-77,95-96,793-794,1504-1544,1723-1788`,
  `packages/opencode/src/provider/provider.ts:1613`,
  `packages/core/src/v1/config/provider.ts` (Model.options),
  `packages/opencode/node_modules/@openrouter/ai-sdk-provider/dist/index.{d.ts:381-396,js:3628-3631,3824-3826}`.
