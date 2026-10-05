---
id: KILO-4
title: >-
  Second OpenRouter key: duplicate OpenRouter provider under its own id with
  full model metadata
status: To Do
assignee: []
created_date: '2026-10-05 10:11'
labels: []
dependencies: []
type: enhancement
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
User has two OpenRouter accounts/keys but the Settings UI only allows configuring one OpenRouter provider. A manually-added custom provider (custom URL) loses OpenRouter's model metadata — models don't recognize image input and are otherwise limited — because a config entry for an unknown id gets no models.dev catalog entry, so every model must be hand-declared without modalities.

Facts from codebase research:
- The OpenRouter model metadata comes from the models.dev catalog entry for id `openrouter` (packages/core/src/models-dev.ts), loaded at runtime; the npm SDK `@openrouter/ai-sdk-provider` is bundled (packages/opencode/src/provider/provider.ts BUNDLED_PROVIDERS).
- Provider id is NOT used for API routing (endpoint/key come from api + options.baseURL/options.apiKey); it only affects display and a few id-keyed patches (custom loader injecting HTTP-Referer/X-Title headers for id `openrouter`, OpenRouter plugin header/alias patch, alias-model deletion for ids openai/github-copilot/openrouter).
- Config `provider` is a record keyed by arbitrary string (packages/core/src/v1/config/config.ts), and mergeProvider merges by exact id only — so a second id like `openrouter-private` is technically possible but gets no catalog models today.

DESIGN DECISION TO LOCK BEFORE IMPLEMENTATION (user lean: hardcoded duplicate; agent assessment: not the only option):
- Option A — hardcoded duplicate provider: clone the models.dev `openrouter` catalog entry under a second id (e.g. `openrouter-private`) at catalog-load time, implemented in a Kilo-owned path (e.g. packages/opencode/src/kilocode/provider/) to minimize upstream conflicts. Gives full metadata automatically and appears in Settings/connect flows and the auth-store login flow (key storage: auth store, keyed by provider id — user's chosen location). Downside: fixed name, only helps OpenRouter users of this fork.
- Option B — generic "clone provider" config key (e.g. provider entry with a `cloneOf`/`extends` reference): generalizes to any provider, but touches the upstream-shared config schema (packages/core/src/v1/config/provider.ts) and needs kilocode_change markers; more work.
- Note: plain config entry with hand-declared models is NOT sufficient — that is exactly what the user tried and it lost modalities/image support.

User's chosen key storage: auth store entry (auth.json, keyed by provider id, packages/opencode/src/auth/index.ts). Verify the connect/login flow accepts the new id (it should if the provider appears in the provider list).

Naming: display name "OpenRouter Private" (or similar) so the two are distinguishable in the picker.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Second provider appears in the JetBrains plugin model finder with the full OpenRouter model catalog, including image-input support (modalities) and cost/limit metadata identical to the openrouter provider's models
- [ ] #2 Second provider can be authenticated independently via the auth store (keyed by its own provider id) without touching the first OpenRouter key
- [ ] #3 Both providers can be used simultaneously in one session without id collisions or header/alias patching leaking between them
- [ ] #4 Implementation lives in Kilo-owned paths (kilocode directories) so upstream merges stay clean; any shared-file edit carries kilocode_change markers
- [ ] #5 User's stated end condition met: pick the second provider from the model finder at the bottom of the JetBrains UI and post images with it
<!-- AC:END -->
