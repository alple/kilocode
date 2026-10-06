# Per-project parameter store with on-demand-only re-resolution

JetBrains sessions need inheritance for model/route/effort that survives IDE restarts without cross-populating between IntelliJ projects (multiple projects open side by side must keep separate defaults). `model.json` is user-global with no directory dimension anywhere in the chain, so it cannot be the home. We build a **project-scoped `PersistentStateComponent`** (workspace.xml storage, `KiloLogSettingsService` precedent) in a Deluxe-owned file as the single JetBrains write target: model pick per agent, reasoning effort and route per (provider, model), plus the remembered mode/agent — which moves out of IDE-global `KiloPluginSettings`. JetBrains stops writing `model.json`, which becomes a read-only legacy tier (the CLI TUI keeps writing it); the model resolution chain gains the project tier on top and is otherwise unchanged. Codified stability contract: a session's parameters re-resolve **only on explicit user actions** (pick, agent switch, unfreeze+edit) — background events (config patch, catalog reload, defaults change in settings) never mutate an open session's values, and new sessions resolve fresh from the store.

## Considered Options

- **`model.json` + directory key** — contradicts ADR-0001's no-`model.json`-schema-change boundary, touches the CLI schema and all its consumers, and the file stays user-global under the hood. Rejected.
- **Project-level `PropertiesComponent` flat keys** — same semantics but three typed maps become string-encoded key soup with hand-namespaced keys. Rejected in favor of the structured `PersistentStateComponent`.
- **IDE-global persistence (status quo)** — cross-populates between projects; rejected per the per-project requirement (mode/agent already cross-populate today via `KiloPluginSettings`; that moves per-project too).
- **Auto-recovery of dead routes** (revert to Auto + notice on staleness) — rejected in favor of keep-pin-until-re-picked: nothing changes without an explicit user action, even when the inherited/pinned route is unavailable. A notice plus the refreshed route list makes the recovery path visible; a dead pin (`allow_fallbacks: false`) fails requests hard until re-picked — accepted consequence.

## Consequences

- No `ModelStateDto`/`model.json`/CLI/RPC change; the ADR-0001 protocol boundary holds.
- The forced first-variant default (`selectResolvedModel`, `SessionController.kt`) is dropped: never-picked effort starts Auto (null variant = provider default); the machine-global `model.json` variant map stays a lower read tier.
- The dead `ProvidersDto.defaults` pre-READY branch in `SessionSelection.kt` is deleted; pre-READY bootstrap degrades to the first picker item (the wire carries bare model IDs the parser could never read).
- Today's broad `syncModelSelection` re-fires on background events get narrowed to the on-demand-only contract.
- `model.json` remains a live store for the CLI TUI; the JetBrains plugin treats it as read-only.
