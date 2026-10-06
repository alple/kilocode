# Deluxe-owned route picker with per-turn route transport

The JetBrains plugin needs per-model server routing (OpenRouter sub-providers like parasail/digitalocean) plus a locked session parameter bar (model+route+effort freeze at first message). This fork is the Deluxe edition of Kilo Code (`upstream = Kilo-Org/kilocode`): upstream picker code churns on every sync, so we build the route/effort/lock UI as a **Deluxe-owned parameter component in a fork-marked package** (first such convention in this plugin), leaving upstream `ModelPicker`/`PickerPopup` rows/renderer untouched for routes — the only upstream edit is the session-header binding swap, at the ~12-line scale of the existing rewind-feature precedent. The list reuses upstream generic `PickerPopup` as the shell (2 usages, stable) with fork-owned renderer/rows. Routes ride the request **per-turn like reasoning variants** (variant precedent), so no `ModelSelectionDto`/`model.json` schema change. Which providers offer route choice is decided by a **Deluxe-branch-only "enable model routing" option** with a configurable provider-id list (defaults: `openrouter`, `openrouter-private` from KILO-4) — no new provider-state DTO fields, keeping the shared protocol unpolluted.

## Considered Options

- **Extend upstream `ModelPicker`/`PickerPopup` in place** — feature reaches all four embed sites for free, but touches the highest-churn upstream files (popup internals, renderer, rows, tests) and leaks route concepts into settings/worktree dialogs. Rejected: maximizes upstream-conflict surface, violating the Deluxe strategy.
- **Wrapper/subclass around upstream `ModelPicker`** — fewer edit points but breaks on upstream refactors of picker internals. Rejected: fragile against sync.
- **CLI-declared route-capability field on `ProviderDto`** — cleanest matching data, but adds a field to shared provider state the user wants kept unpolluted; matching now lives in the fork-only option instead.
- **Config-only routes (kilo.json `options.provider.order`)** — works today (verified, KILO-7.5) and remains the interim/fallback for non-OpenRouter providers, but was rejected as the *mechanism*: the decided UX is in-picker route choice.

## Consequences

- Implementation planning must name the additive RPC/HTTP surface for the live route list (`GET /api/v1/models/{author}/{slug}/endpoints` proxied by the CLI, public, unpaginated) and the session-local freeze state.
- The fork-marked package convention set here (`ai.kilocode.client.deluxe…` or `client/session/ui/param`) should be reused for future Deluxe-owned JetBrains surfaces.
- Selection unit stays `(providerID, modelID)`; a route change applies from the next message onward; earlier turns keep their parameters.
