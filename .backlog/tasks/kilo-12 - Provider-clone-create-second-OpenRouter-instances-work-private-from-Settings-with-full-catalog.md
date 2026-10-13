---
id: KILO-12
title: >-
  Provider clone: create second OpenRouter instances (work/private) from
  Settings with full catalog
status: To Do
assignee: []
created_date: '2026-10-13 13:15'
labels: []
dependencies: []
type: enhancement
ordinal: 35000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Supersedes KILO-4 (hardcoded clone) per user decision 2026-10-13. Mechanism re-decided after the Deluxe reset: a Settings-driven **provider clone** for OpenRouter.

## Problem
User has two OpenRouter accounts (work, private) but only one `openrouter` provider exists. Workarounds fail: a custom provider with an overridden URL is forced to hand-declare models (name/reasoning/modalities only — no costs/limits) and loses image-input support, because a config entry for an unknown id gets no models.dev catalog entry.

## Decided design (user-confirmed 2026-10-13)
- **Provider clone** (see GLOSSARY.md): user creates a second OpenRouter instance from the VS Code Settings providers dialog — types a display name, id is auto-derived (e.g. "OpenRouter Work" → `openrouter-work`), pastes its API key.
- **OpenRouter only**: the clone affordance is offered for OpenRouter, not arbitrary providers (OAuth providers and id-keyed custom loaders don't transfer cleanly).
- **Config-driven substrate**: the dialog writes a config entry (e.g. `"openrouter-work": { "cloneOf": "openrouter", "name": "OpenRouter Work" }` to kilo.json via the existing config-update API); the CLI expands `cloneOf` at catalog-load time into a full copy of the models.dev `openrouter` entry (same models with modalities/costs/limits, same `@openrouter/ai-sdk-provider` npm, same api URL).
- **Env var**: clones do NOT inherit `env: ["OPENROUTER_API_KEY"]` — setting that env var must not authenticate clones (user decision). Keys are stored per clone id in the auth store (auth.json), no code change needed there.
- **Parity**: OpenRouter id-keyed behaviors must apply to clones — branding headers (HTTP-Referer/X-Title, handled in Kilo-owned `patchCustomLoaderResult`/`kiloCustomLoaders`) and the invalid `gpt-5-chat` alias-model deletion (one-line kilocode_change in shared `packages/opencode/src/provider/provider.ts` near lines 1779-1785, or prune during clone expansion in Kilo-owned code — prefer the latter).
- **Supersede**: replaces KILO-4's locked Option A (hardcoded `openrouter-private`); KILO-4 closed with a pointer.

## Post-reset code facts (verified 2026-10-13)
- Catalog assembly happens in Kilo-owned `packages/opencode/src/provider/models.ts` `overlay()` — already injects synthetic providers (Anaconda Desktop); clone expansion fits here with no upstream merge surface for the mechanism itself.
- Shared config schema `packages/core/src/v1/config/provider.ts` (`ProviderConfig.Info`) silently drops unknown keys → needs one added optional `cloneOf` field, marked kilocode_change; mirror to cloud schema only if the key must round-trip cloud config (likely not needed — clones are machine-local).
- Custom provider npm enum (webview `CUSTOM_PROVIDER_PACKAGES`) excludes `@openrouter/ai-sdk-provider` — the dialog path needs its own affordance, not the generic custom-provider form.
- VS Code settings provider list comes from CLI `provider.list` (`fetchProviderData` in `packages/kilo-vscode/src/provider-actions.ts`) — clones appear automatically once in the catalog; auth per id works via existing `auth.set`/`connectProvider`.
- A prior Deluxe branch referenced `openrouter-private` in JetBrains route-picker defaults (archive/deluxe-my-features), but the CLI-side clone was never implemented — no code to restore.

## Notes
- Do not hand-edit models; the whole point is catalog inheritance.
- Keep the JetBrains side out of scope: clones appear wherever providers are listed from the CLI (JetBrains model finder included) with no JetBrains-specific work.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Settings providers dialog offers creating an OpenRouter clone: user types a display name (id auto-derived, e.g. "OpenRouter Work" → openrouter-work) and enters an API key; the clone appears as its own provider in the provider list
- [ ] #2 #2 Clone has the full OpenRouter model catalog from models.dev — modalities/vision, costs, limits identical to the openrouter provider's models — not hand-declared models
- [ ] #3 #3 Clone authenticates independently: its own key stored in the auth store under its own provider id; does not read OPENROUTER_API_KEY and does not share the original OpenRouter key
- [ ] #4 #4 Both the original OpenRouter and all clones work simultaneously in one session without id collisions; OpenRouter parity patches (branding headers, invalid gpt-5-chat alias-model deletion) apply to clones too
- [ ] #5 #5 Removing a clone from Settings removes its config entry and stored key; the original OpenRouter provider is unaffected
- [ ] #6 #6 Mechanism is config-driven and generic on the CLI side (cloneOf expansion lives in Kilo-owned catalog assembly in packages/opencode/src/provider/models.ts); shared-file edits carry kilocode_change markers and stay minimal
- [ ] #7 #7 User can create at least two clones (work + private) and pick each from the VS Code settings/model picker and post images with a vision model from a clone
<!-- AC:END -->
