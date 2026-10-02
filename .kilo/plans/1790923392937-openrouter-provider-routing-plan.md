# OpenRouter provider routing selection (JetBrains plugin)

Session: `ses_f04a5b856ffe9v4k0y8L63R3Gk` ("OpenRouter provider routing selection") — investigation complete, crashed while writing this plan. This file is the recovery.

## Goal

In the Kilo JetBrains plugin: pick which OpenRouter provider (e.g. DeepInfra, DigitalOcean, Vercel) serves a given model's requests **before sending**, and **log** which provider actually served each step. Display is secondary.

## User decisions (from the failed session)

1. **Primary flow**: choose provider before sending + log the provider used. Passive display in transcript is less important.
2. **Routing semantics**: **hard pin** — `{ only: ["<Provider>"], allow_fallbacks: false }`. OpenRouter errors instead of silently rerouting.

## Verified findings (evidence)

| # | Finding | Where |
|---|---|---|
| 1 | OpenRouter SDK v2.9.0 already captures the routed provider into `providerMetadata.openrouter.provider` on finish (streaming + non-streaming) | `@openrouter/ai-sdk-provider` dist, streaming: `if (value.provider) provider = value.provider` |
| 2 | SDK routing must be set at **model construction**: `openrouter(modelId, settings)` / `sdk.languageModel(modelId, settings)` pass `settings.provider` into the request body (`provider: this.settings.provider`) | SDK dist lines ~3593, ~4986; per-call `providerOptions.openrouter.provider` is NOT read in v2 (only `reasoning_details`, `annotations`) |
| 3 | CLI `getLanguage` fallback is `sdk.languageModel(model.api.id)` with **no settings**; custom loaders get `{...provider.options, ...model.options}` — per-model config `options` already exists in the config schema | `packages/opencode/src/provider/provider.ts:2009-2038`; `packages/core/src/v1/config/provider.ts` (per-model `options: Record<string, any>`) |
| 4 | `kiloCustomLoaders` (kilo-owned) is the seam for an `openrouter` loader — currently no `getModel` for openrouter, so default no-settings path is used | `packages/opencode/src/kilocode/provider/provider.ts:191` |
| 5 | `StepFinishPart` schema already carries Kilo extensions (`model`, `generationID`, `vercelID`, `metrics`, `time`) but **drops the routed provider name**; processor has the extraction point ready | `packages/schema/src/v1/session.ts:241-274`; `packages/opencode/src/session/processor.ts:652-701` (kilocode_change blocks) |
| 6 | JetBrains backend parses `step-finish` but drops `model`/`generationID`/metrics; `PartDto` has no field for them | `packages/kilo-jetbrains/backend/src/main/kotlin/ai/kilocode/backend/cli/KiloCliDataParser.kt:1282-1308` |
| 7 | JetBrains `StepFinish` model feeds the header timeline; tooltip title is plain "Step finish" | `frontend/.../session/model/Message.kt:113`, `SessionModel.kt:578,863` |
| 8 | Plugin config RPC exists (`config(directory)` + `updateConfig` via CLI `GET/PATCH /config`) but `buildConfigPatch` is a curated allowlist — needs a new entry for provider-model options | `backend/.../rpc/KiloWorkspaceRpcApiImpl.kt:199`; `KiloCliDataParser.kt:1008` |
| 9 | Provider list per model is **public, no auth**: `GET https://openrouter.ai/api/v1/models/{model}/endpoints` → `data.endpoints[]` with `provider_name`, pricing, quantization, uptime | verified live with `z-ai/glm-4.5-air` |
| 10 | Model picker UI has a details panel (`ModelDetailsPanel.kt`) where a "Routing" row fits | `frontend/.../session/ui/model/ModelDetailsPanel.kt` |

## Plan

### A. CLI: apply the routing pin (BYOK `openrouter` provider)

1. `packages/opencode/src/kilocode/provider/provider.ts` — add an `openrouter` entry to `kiloCustomLoaders`:
   ```ts
   openrouter: () =>
     Effect.succeed({
       autoload: false,
       async getModel(sdk: any, modelID: string, options?: Record<string, any>) {
         const routing = options?.provider
         if (routing == null) return sdk.languageModel(modelID)
         return sdk.languageModel(modelID, { provider: routing })
       },
       options: {},
     }),
   ```
   Kilo-owned file — no markers needed. `getLanguage` passes `{...provider.options, ...model.options}` into it, so config becomes the user surface:
   ```json
   { "provider": { "openrouter": { "models": { "z-ai/glm-5.3-flash": {
         "options": { "provider": { "only": ["DeepInfra"], "allow_fallbacks": false } } } } } } }
   ```
2. **Cache invalidation** (must verify during implementation): `getLanguage` caches language models in `s.models` keyed by `providerID/modelID` (`provider.ts:2012-2030`). A changed pin must take effect — either include routing settings in the cache key or clear `s.models` when config updates. Confirm how config change currently propagates to `Provider` state and pick the minimal fix.

### B. CLI: capture + persist the routed provider name

3. `packages/opencode/src/session/processor.ts` step-finish case — inside the existing `kilocode_change` block add:
   ```ts
   const routedProvider = isRecord(value.providerMetadata?.openrouter)
     ? value.providerMetadata.openrouter.provider
     : undefined
   ```
   and `...(routedProvider ? { routedProvider } : {})` in the `session.updatePart` call.
4. `packages/schema/src/v1/session.ts` — `StepFinishPart`: add `routedProvider: Schema.optional(Schema.String)` inside the `kilocode_change` block. Optional, so legacy sessions decode.
5. Regenerate SDK after schema change: `./script/generate.ts` from root.

### C. JetBrains: log + carry the routed provider

6. `shared/.../rpc/dto/ChatDto.kt` — `PartDto`: add `routedProvider: String? = null` and `model: PartModelDto? = null` (data class with `providerID`/`modelID`).
7. `backend/.../cli/KiloCliDataParser.kt` `parsePart` — read `obj.str("routedProvider")` and `obj["model"]`.
8. `frontend/.../session/model/Message.kt` — `StepFinish`: add `provider: String?`, `model`; `SessionModel.kt:578` apply them.
9. `shared/.../log/ChatLogSummary.kt` — append `routed=<provider>` (and `model=pid/mid` when routed differs) to the step-finish part summary line.
10. Timeline tooltip: `SessionModel.kt:863` title → `"Step finish" + (provider != null ? " · $provider" : "")` — one line, satisfies the "visible" ask without a display overhaul.

### D. JetBrains: choose the provider before sending

11. **Backend**: new fetch (OkHttp, already bundled) to `GET https://openrouter.ai/api/v1/models/{modelID}/endpoints`, mapped to a DTO: `provider_name`, quantization, prompt/completion pricing, uptime_last_30m. Exposed via a workspace RPC method (e.g. `openrouterEndpoints(directory, modelID)`), mirroring the existing config RPC pattern.
12. **UI**: in `ModelDetailsPanel.kt`, when the selected provider is `openrouter`, add a "Routing" row → popup listing "Auto (default)" + endpoints (name, price in/out, quantization, uptime). Selection persists via config.
13. **Persistence**: extend `ConfigPatchDto` + `KiloCliDataParser.buildConfigPatch` to write `provider.<pid>.models.<mid>.options.provider` (hard-pin shape from user decision; clearing the row removes the key → auto). Backend Kotlin — no upstream-conflict concerns.
14. **Pre-send log**: when a pin is active, backend logs `routing=<only list>` at request/session start so the log shows what will be pinned before the response arrives; the actual provider arrives in `step-finish.routedProvider` (step 9).

### Tests

- CLI: `kiloCustomLoaders` openrouter loader passes `{ provider }` settings only when present; `StepFinishPart` optional-field decode (legacy + new shape).
- JetBrains: `KiloCliDataParserTest` — step-finish part with `routedProvider`/`model`; `buildConfigPatch` roundtrip for provider-model options; `ChatLogSummary` routed= line.
- Manual: sandbox run (`./gradlew runIdeSplitMode`) — pin a provider on z-ai/glm-5.3-flash, send, confirm log shows pin + routed provider, confirm request errors with an unknown provider name (hard pin proof).

### Risks / open details

- **Model cache invalidation** (A.2) — the one unresolved CLI detail; verify config-change propagation before choosing cache-key vs. invalidation.
- **Kilo gateway path**: models served through the `kilo` provider route via OpenRouter server-side under Kilo's control; per-model routing pins are scoped to the BYOK `openrouter` provider (the user's path). Out of scope unless the user wants it for kilo too.
- OpenRouter endpoints API is public but unversioned; tolerate missing/renamed fields defensively (Kotlin defaults).

## Out of scope

- Full transcript display redesign (user: "less interested in display").
- Routing pins for non-OpenRouter providers.
