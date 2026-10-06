---
id: KILO-8.2
title: 'CLI: route support — endpoints proxy + per-turn route transport'
status: In Progress
assignee:
  - '@dev1'
created_date: '2026-10-06 13:04'
updated_date: '2026-10-06 13:27'
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
