---
id: KILO-4
title: >-
  Second OpenRouter key: duplicate OpenRouter provider under its own id with
  full model metadata
status: To Do
assignee: []
created_date: '2026-10-05 10:11'
updated_date: '2026-10-05 10:15'
labels: []
dependencies: []
type: enhancement
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
User has two OpenRouter accounts/keys but the Settings UI only allows configuring one OpenRouter provider. A manually-added custom provider (custom URL) loses OpenRouter's model metadata — models don't recognize image input and are otherwise limited — because a config entry for an unknown id gets no models.dev catalog entry, so every model must be hand-declared without modalities.

DECISION LOCKED (user confirmed): Option A — hardcoded duplicate provider. Clone the models.dev `openrouter` catalog entry under a second id (`openrouter-private`, display name "OpenRouter Private") at catalog-load time, implemented in a Kilo-owned path (packages/opencode/src/kilocode/provider/) so no shared upstream files are edited. Option B (generic cloneOf config key) was rejected: touches upstream-shared config schema, more merge surface than the user wants.

Facts from codebase research:
- The OpenRouter model metadata comes from the models.dev catalog entry for id `openrouter` (packages/core/src/models-dev.ts), loaded at runtime; the npm SDK `@openrouter/ai-sdk-provider` is bundled (packages/opencode/src/provider/provider.ts BUNDLED_PROVIDERS).
- Provider id is NOT used for API routing (endpoint/key come from api + options.baseURL/options.apiKey); it only affects display and a few id-keyed patches (custom loader injecting HTTP-Referer/X-Title headers for id `openrouter`, OpenRouter plugin header/alias patch, alias-model deletion for ids openai/github-copilot/openrouter). Those id-keyed patches will NOT fire for the clone id — verify header-injection parity in implementation (the clone should behave like real OpenRouter toward the API).
- Config `provider` is a record keyed by arbitrary string (packages/core/src/v1/config/config.ts), and mergeProvider merges by exact id only — so a second id merges cleanly without dedup.
- Plain config entry with hand-declared models is NOT sufficient — that is exactly what the user tried and it lost modalities/image support.

Implementation outline:
1. In a Kilo-owned module, after catalog load, clone the `openrouter` entry to id `openrouter-private` (name "OpenRouter Private", same models/npm/api).
2. Ensure the clone appears in the provider list the JetBrains plugin and model finder consume (including the id-keyed alias-deletion and header behaviors — extend the openrouter-specific patches to the clone id, marked kilocode_change in the shared switch sites, or routed via the Kilo-owned patch function patchCustomLoaderResult which already keys on id lists).
3. Key storage: auth store entry (auth.json, keyed by provider id, packages/opencode/src/auth/index.ts) via the normal connect/login flow — user's chosen location. Verify the login flow accepts the new id.
4. Verify in JetBrains plugin: full model catalog with modalities/image support, simultaneous use of both providers in one session without collisions.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Second provider appears in the JetBrains plugin model finder with the full OpenRouter model catalog, including image-input support (modalities) and cost/limit metadata identical to the openrouter provider's models
- [ ] #2 Second provider can be authenticated independently via the auth store (keyed by its own provider id) without touching the first OpenRouter key
- [ ] #3 Both providers can be used simultaneously in one session without id collisions or header/alias patching leaking between them
- [ ] #4 Implementation lives in Kilo-owned paths (kilocode directories) so upstream merges stay clean; any shared-file edit carries kilocode_change markers
- [ ] #5 User's stated end condition met: pick the second provider from the model finder at the bottom of the JetBrains UI and post images with it
<!-- AC:END -->
