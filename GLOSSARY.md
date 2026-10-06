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
