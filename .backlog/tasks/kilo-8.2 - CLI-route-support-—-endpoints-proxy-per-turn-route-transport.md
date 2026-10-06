---
id: KILO-8.2
title: 'CLI: route support — endpoints proxy + per-turn route transport'
status: Testing
assignee:
  - dev1
created_date: '2026-10-06 13:04'
updated_date: '2026-10-06 15:13'
labels: []
dependencies: []
parent_task_id: KILO-8
priority: high
type: task
ordinal: 22000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Decisions implemented

[Settle what "choosing a provider" means in the JetBrains model picker](KILO-7.2) #3 (route data live via CLI) and per-turn application; [OpenRouter endpoints survey](KILO-7.5) (endpoints API public/unpaginated, pin semantics, tag slugs); [ADR-0001](docs/adr/0001-deluxe-owned-route-picker.md) (routes ride per-turn like reasoning variants).

## Scope

**1. Route-list proxy endpoint.** New Kilo-owned instance API group + handler proxying OpenRouter `GET /api/v1/models/{author}/{slug}/endpoints`:
- Public endpoint, unpaginated, single array; 404 passthrough for unknown models; **dedupe rows by `tag`** (same tag can repeat, e.g. two baseten/fp8 rows); tolerate churn/sparse lists.
- **Auth posture (decided 2026-10-06): attach the provider's stored OpenRouter key when one exists (BYOK openrouter), else call publicly** — latency/throughput percentiles are auth-gated (null unauthenticated); price/uptime/quantization always present.
- Normalized response rows: `tag`, `providerName`, `name`, `quantization`, `status` (0 = healthy), uptime windows, pricing, contextLength, maxCompletionTokens, supportedParameters.
- Lands in the Kilo-owned groups dir `packages/opencode/src/kilocode/server/httpapi/groups/*` (registered in `api.ts:99-118`), handler under `handlers/*`; the shared-file registration line carries a `kilocode_change` marker.

**2. Per-turn route transport.** Additive optional field on the prompt input carrying the OpenRouter routing tag (e.g. `"parasail/fp4"`):
- CLI: `SessionPrompt.PromptInput` (`packages/opencode/src/session/prompt.ts:2505-2534`), `PromptPayload` in `groups/session.ts:88` (`prompt`/`promptAsync` endpoints `:348-373`); resolve route → `provider: { order: ["<tag>"], allow_fallbacks: false }` (exact KILO-7.5 pin semantics; absent field = Auto/provider-default routing) via a **kilo-owned helper under `packages/opencode/src/kilocode/`**, called from `session/llm/request.ts` and merged at highest precedence alongside variant (existing `kilocode_change` block `request.ts:109-113`). Route is pure per-turn input — no session-state storage on the CLI.
- Works for both OpenRouter transports (BYOK `@openrouter/ai-sdk-provider` spreads into body verbatim; kilo-gateway path passes through `kiloProviderOptions` under the `openrouter` namespace).

**3. SDK regen** — `./script/generate.ts` from repo root (new endpoint + PromptInput field).

## Hard constraints

- **No `ModelStateDto`/`model.json` change; no new provider-state DTO fields** (KILO-7.3/7.6 boundary).
- Shared opencode files edited minimally, marked with `kilocode_change`.
- kilo.json `options.provider.order` remains the working config-only interim (non-OpenRouter providers stay there).

## Verification

- CLI-side tests for the proxy handler (dedupe by tag, 404 passthrough, key/no-key call shapes) and the route→options resolve (merged at highest precedence; absent = no-op). SDK builds after regen.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 GET /kilo/routes/:author/:slug proxies the OpenRouter endpoints API: normalized rows (tag, providerName, name, quantization, status, uptime windows, pricing, contextLength, maxCompletionTokens, supportedParameters), dedupe by tag (first wins), missing/malformed fields tolerated, upstream 404 → NotFound passthrough, other upstream failures → 502-style error with message, invalid path segments → BadRequest
- [x] #2 Auth posture: stored BYOK OpenRouter key (auth.get(openrouter), type api) attached as Bearer when present, public call otherwise — both header shapes tested
- [x] #3 Per-turn transport: optional route field on the prompt input reaches the LLM request for that turn only — no session-state persistence anywhere (no message storage, no model.json writes); absent route = no-op (Auto/provider-default routing)
- [x] #4 Resolution: kilo-owned helper emits provider {order: [tag], allow_fallbacks: false} merged at highest precedence (after variant) in the request options, gated to the OpenRouter transports (@openrouter/ai-sdk-provider, @kilocode/kilo-gateway)
- [x] #5 SDK regenerated via the documented generate script (new endpoint + prompt field in SDK types), not hand-edited
- [x] #6 Targeted CLI tests + typecheck pass; shared opencode edits are minimal kilocode_change-marked lines; no ModelStateDto/model.json change
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation plan

Design facts (verified in-code + doc-2 survey): `PromptPayload` derives from `SessionPrompt.PromptInput.fields` (no separate edit); both OpenRouter transports converge on the SDK spreading `providerOptions.openrouter` verbatim into the request body (`restOpenrouterOptions` spread), so a route only needs to reach the flat options bag in `request.ts` as `provider: {order: [tag], allow_fallbacks: false}`; route threads explicitly like the `snapshotInitialization` precedent (no Effect-context service — avoids leaking the pin into subagent sessions' fibers).

### 1. Route-list proxy (all Kilo-owned files)
- `src/kilocode/server/httpapi/groups/routes.ts` — `RoutesApi`: group `routes`, root `/kilo/routes`, GET `/:author/:slug`, `query: WorkspaceRoutingQuery`, success `described(Schema.Array(RouteEndpoint))`, errors `[BadRequest, NotFound, RouteListError]`; `.middleware(InstanceContextMiddleware, WorkspaceRoutingMiddleware, Authorization)` like the kilo group.
- `RouteEndpoint` DTO rows: `tag`, `providerName`, `name`, `quantization`, `status`, `uptime {last5m,last30m,last1d}`, `pricing {prompt,completion,inputCacheRead,discount}`, `contextLength`, `maxCompletionTokens`, `supportedParameters` (all nullable except tag/status-shape tolerances; pricing parsed from upstream strings to Finite).
- `src/kilocode/server/httpapi/handlers/routes.ts` — fetch `https://openrouter.ai/api/v1/models/{author}/{slug}/endpoints` with request signal; attach `Authorization: Bearer` from `auth.get("openrouter")` when `type === "api"` (BYOK), else public call; upstream 404 → `HttpApiError.NotFound` (passthrough), other upstream failures → `RouteListError` (message + upstream status); parse `data.endpoints`, drop rows without a usable `tag`, dedupe by tag keeping first occurrence, tolerate missing fields; validate path segments (`[A-Za-z0-9._-]+`, no slashes) → BadRequest.
- Register: `server/routes/instance/httpapi/api.ts` adds import + `.addHttpApi(RoutesApi)` INSIDE the existing `kilocode_change` blocks (no new markers needed); `src/kilocode/server/httpapi/server.ts` adds `routesHandlers` to the provide list.

### 2. Per-turn route transport (thread-through, snapshotInitialization precedent)
- `src/session/prompt.ts` (shared, markers): `PromptInput.route` optional string (near `variant`); `LoopInput.route`; `prompt()` loop call gains `route: input.route` (inside existing kilocode_change block); runLoop's `handle.process({...})` gains `route: input.route` line.
- `src/session/llm.ts` (shared): `StreamInput.route?: string` with marker.
- `src/session/llm/request.ts` (shared): `PrepareInput.route?: string`; in the existing merge block resolve `KiloRoute.resolve(...)` and merge LAST (highest precedence): `mergeOptions(..., variant), routeOptions`.
- New `src/kilocode/session/route.ts` — `KiloRoute.resolve({route, model})`: returns undefined when route is absent/empty or `model.api.npm` is not an OpenRouter transport (`@openrouter/ai-sdk-provider` | `@kilocode/kilo-gateway`); else `{ provider: { order: [route], allow_fallbacks: false } }`.
- No persistence: route never touches message storage; commands/subagents/resume turn flows carry no route (no-op).

### 3. SDK regen + verification
- `./script/generate.ts` from repo root (new endpoint + PromptInput field → SDK).
- New tests: `test/kilocode/server/httpapi-routes.test.ts` (web-handler harness like httpapi-kilo-edit.test.ts: dedupe by tag, 404 passthrough, Authorization header key/no-key shapes, malformed rows tolerated, BadRequest on bad segments) and `test/kilocode/session/route.test.ts` (absent/no-op, gate by npm, pin shape).
- Checks: targeted `bun test`, `bun run typecheck` (packages/opencode), `bun run knip` (packages/kilo-vscode unaffected), opencode-annotations check for shared-file markers.

### Risks/notes
- Shared-file edits are ~6 marker lines across prompt.ts/llm.ts/request.ts + 1 registration block line set in api.ts; all other files Kilo-owned.
- `kiloProviderOptions` passes the openrouter namespace through verbatim, so the gateway transport needs no changes; verified `restOpenrouterOptions` spread reaches the body as `provider`.
<!-- SECTION:PLAN:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-06 14:06
---
Handoff for implementation (2026-10-06): researched and planned in a prior session, deliberately NOT implemented there — this session was spent verifying every fact in the Implementation Plan against the code (line anchors below the plan are current as of that day; re-grep if the tree moved). The plan is self-contained: pipeline facts, thread-through chain, file-by-file steps, gotchas, test harness pattern, and check commands are all recorded — no re-research needed. OpenRouter API facts live in Backlog document doc-2 (KILO-7.5 survey). Next session: claim (assign) + In Progress, then implement plan sections 1→3 in order. Note: repo git identity is intentionally left as the user's own — do not touch git config (AGENTS.md → Anonymity).
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented KILO-8.2 (CLI route support) per the recorded plan.

**1. Route-list proxy (Kilo-owned files)**
- `src/kilocode/server/httpapi/groups/routes.ts` — `RoutesApi`, group `routes`, root `/kilo/routes`, GET `/:author/:slug` with `WorkspaceRoutingQuery`, success `Array(RouteEndpoint)`, errors `[BadRequest, NotFound, RouteListError]`; middleware identical to the kilo group. `RouteEndpoint` rows: `tag` (required), `providerName`, `name`, `quantization`, `status` (0 = healthy), `uptime{last5m,last30m,last1d}`, `pricing{prompt,completion,inputCacheRead,discount}` (upstream string prices parsed to finite numbers), `contextLength`, `maxCompletionTokens`, `supportedParameters`. Optional fields absent upstream are emitted as explicit `null` on the wire (matches the `BackgroundJobInfo` precedent; SDK shows them optional).
- `src/kilocode/server/httpapi/handlers/routes.ts` — proxies `https://openrouter.ai/api/v1/models/{author}/{slug}/endpoints` with the request signal; validates path segments against `[A-Za-z0-9._-]+` → BadRequest; upstream 404 → NotFound passthrough; other upstream statuses / fetch failures / malformed bodies → `RouteListError` (502, message + upstream status); parses `data.endpoints`, drops rows without a usable `tag`, dedupes by tag keeping the first row (verified live: same tag can repeat, e.g. two baseten/fp8 rows; live API shape unchanged since the doc-2 survey — `data.endpoints`, snake_case keys, 404 for unknown models).
- Auth posture: stored BYOK OpenRouter key (`auth.get("openrouter")`, `type === "api"`) attached as `Authorization: Bearer`, public call otherwise; auth read errors degrade to the public call.
- Registered in `server/routes/instance/httpapi/api.ts` (import + `.addHttpApi(RoutesApi)` inside the existing `kilocode_change` blocks) and `src/kilocode/server/httpapi/server.ts` (`routesHandlers` added to the provide list).

**2. Per-turn route transport (thread-through, snapshotInitialization precedent)**
- `src/session/prompt.ts` (shared, 4 marked lines): `PromptInput.route`, `LoopInput.route` (both `Schema.optional(Schema.String)`); `prompt()` passes `route: input.route` into `loop(...)`; `runLoop` passes `route: input.route` into `handle.process(...)` — so `PromptPayload` (derived from `PromptInput.fields`) and both `prompt`/`promptAsync` endpoints carry it with no further edits.
- `src/session/llm.ts`: `StreamInput.route?: string`.
- `src/session/llm/request.ts`: `PrepareInput.route?`; inside the existing agent-metadata marker block the route resolves via `KiloRoute.resolve(...)` and merges LAST: `mergeOptions(..., variant), routeOptions` — highest precedence over variant/model/agent options.
- `src/kilocode/session/route.ts` (new): `resolve({route, model})` returns `{ provider: { order: [tag], allow_fallbacks: false } }` (exact KILO-7.5 pin semantics) only for OpenRouter transports (`@openrouter/ai-sdk-provider`, `@kilocode/kilo-gateway`), trims the tag, no-op otherwise (absent/blank route or other transports). Both transports map the bag into `providerOptions.openrouter` (transform.ts:88-91) which reaches the request body verbatim, so no transport changes were needed.
- No persistence: route never touches message storage or model.json; commands, subagents, resume and title flows carry no route (no-ops).

**3. SDK regen** — `./script/generate.ts` from repo root: new `Routes.list` operation (`RoutesListData/Errors/Responses`, `RouteListError` component) and `route?: string` on `SessionPromptData`/`SessionPromptAsyncData`; `packages/sdk/openapi.json` + generated types updated. Regenerated `cli-reference.md` also picked up a stale-docs refresh for the `kilo pr` session-link commands (unrelated drift repair from the documented regen step).

**Verification** — new tests: `test/kilocode/server/httpapi-routes.test.ts` (7 tests: normalize+dedupe first-wins with unusable rows dropped, Bearer key vs public call header shapes, 404 passthrough, 502 RouteListError for upstream status and network failure, BadRequest for `%20`/`%2F`/quote segments) and `test/kilocode/session/route.test.ts` (4 tests: absent/blank no-op, transport gate, exact pin shape, plain provider tags). Extended `test/kilocode/session-llm-request.test.ts` with a route-pin describe (4 tests: pin for both transports, absent no-op, non-OpenRouter skip, pin outranks variant+model-options provider routing). All pass solo (11 + 21 tests), plus httpapi-session/prompt-queue/steering/promptasync/session-resume/mentions/processor files and the OpenAPI surface tests (30) re-run individually: 0 failures. `bun run typecheck` (packages/opencode + full turbo across 30 packages) and root `bun run lint` clean; `check-opencode-annotations --worktree` passes; prettier clean on new files (`llm.ts` prettier warning is pre-existing, reproducible without this change). Full `test/kilocode` batch shows ~1483 pre-existing environmental failures both with (1494) and without (1483) this change — parallel-run interference ("ManagedRuntime disposed", shared DB), delta is exactly the 15 new tests. No `ModelStateDto`/model.json changes; shared-file edits are 10 marked lines total across prompt.ts/llm.ts/request.ts/api.ts.
<!-- SECTION:FINAL_SUMMARY:END -->
