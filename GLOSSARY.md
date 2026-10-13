# Kilo Deluxe

The Deluxe edition of Kilo Code: a fork of the Kilo Code agent repository (`Kilo-Org/kilocode`) maintained separately, adding capabilities on top of Kilo Code while tracking it upstream.

## Language

**Deluxe**:
This fork's own implementations — components, dialogs, windows, and surfaces added by the Deluxe edition rather than inherited from Kilo Code. New functionality should live in Deluxe-owned files.
_Avoid_: fork-owned, custom, lux (synonyms seen in discussion; prefer Deluxe)

**Upstream**:
The Kilo Code repository (`Kilo-Org/kilocode`) via the `upstream` remote — the source this fork syncs from. Conflict surface is judged against Upstream.
_Avoid_: kilo code repo (ambiguous with this repo), origin (that's the fork's own remote)

**Kilo Code UI**:
Code inherited from Upstream — including the shared CLI UI and the JetBrains plugin's Upstream-synced components. Edits inside it must stay minimal; new surfaces belong in Deluxe files.
_Avoid_: shared code (overbroad — it doesn't say which fork)

**Lux build**:
A local, unsigned JetBrains plugin build channel (`ai.kilocode.jetbrains.lux` plugin id, `script/build-lux.sh`) that installs side-by-side with published releases. A packaging concept, not a synonym for Deluxe code.
_Avoid_: using "lux" to mean Deluxe-owned code

**Route**:
The specific serving server a model's request is pinned to inside its provider (e.g. OpenRouter's parasail or digitalocean endpoint for one model). Chosen per turn, like reasoning effort.
_Avoid_: sub-provider (internal jargon), provider (ambiguous with catalog provider), endpoint (means the HTTP API elsewhere)

**Parameter lock**:
The session state in which model, route, and effort are frozen as one from a session's first message until the user explicitly unfreezes; changes apply from the next message onward.
_Avoid_: freeze (verb form is fine, "freeze" as noun avoided), pin (reserved for route pinning)

**Project store**:
The per-IntelliJ-project persisted record of the user's last picks — model per agent, reasoning effort and route per (provider, model), plus the remembered mode/agent. The seed a new session inherits from; JetBrains-side and Deluxe-owned.
_Avoid_: model.json defaults (that file is machine-global), session prefs (in-memory, per session)

**Route preset**:
A named favorite combining provider, model, route, and reasoning effort with user-assigned labels. Stored once at IDE level; shown in every project whose name matches one of its labels; editing it updates every project it appears in.
_Avoid_: favorite preset, profile, template

**Label**:
A user-created tag on a route preset. A label matching an IntelliJ project's name scopes the preset into that project's session popup; unlabeled presets are visible in no project popup.
_Avoid_: tag (synonym avoided), group, category

**Flat favorite**:
The legacy `(provider, model)` star pair persisted in `model.json` by the CLI. Still live for the TUI and the upstream JetBrains pickers; the session popup no longer reads it.
_Avoid_: model favorite, old favorite

**Provider clone**:
A user-created second instance of one catalog provider (OpenRouter only), with its own id, display name, and API key, sharing the source's full model catalog (modalities, costs, limits). Created from the Settings providers dialog; stored as a config entry whose expansion into a catalog provider happens in the CLI.
_Avoid_: duplicate provider (reads like a bug), custom provider (that's the hand-declared-models flow that loses metadata), second key (describes the credential, not the provider)
