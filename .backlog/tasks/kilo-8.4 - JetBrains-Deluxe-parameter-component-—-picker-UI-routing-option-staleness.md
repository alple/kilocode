---
id: KILO-8.4
title: 'JetBrains: Deluxe parameter component — picker UI, routing option, staleness'
status: To Do
assignee: []
created_date: '2026-10-06 13:05'
updated_date: '2026-10-06 13:05'
labels: []
dependencies:
  - KILO-8.2
  - KILO-8.3
parent_task_id: KILO-8
priority: high
type: enhancement
ordinal: 24000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Decisions implemented

[Decide the picker shape](KILO-7.3) + [ADR-0001](docs/adr/0001-deluxe-owned-route-picker.md) (Deluxe-owned component, fork-marked package, per-turn route transport); [Settle what "choosing a provider" means](KILO-7.2) (in-picker route choice, freeze semantics); [Route-support scope](KILO-7.6) (OpenRouter-only v1, enable-model-routing option); staleness from [new-session defaults](KILO-7.4) #4 + ADR-0002 (keep-pin).

## Scope

**1. Fork-marked package — first convention in this plugin:** `ai.kilocode.client.deluxe` (decided 2026-10-06; no marker convention needed in kilo-jetbrains paths). All new files live there.

**2. Deluxe parameter component** (session header):
- Rows for **model + route + reasoning effort**, plus a **lock chip**: model+route+effort editable until the session's **first message**, then they lock **as one**; unfreezing is an explicit user action; any change applies **from the next message onward**; frozen selection sticky per session. New sessions start unlocked with inherited (store) values as ordinary editable values.
- List UI **reuses generic `PickerPopup`** (`client/ui/picker/PickerPopup.kt`, 2 usages, stable) as the shell with **fork-owned renderer/rows** (route data: tag, provider name, quantization, status, uptime, pricing; Auto row = no pin). `ReasoningPicker` is JBPopup-based — not the shell precedent.
- **Upstream edit limited to the session-header binding swap**: `PromptPanel.kt:148-153` / `SessionUi.kt:705-724` (~12-line scale, rewind precedent). Models settings, New Worktree dialog, AgentsConfigurable keep the upstream picker unchanged.

**3. Route-list bridge**: JetBrains backend fetch of the CLI route-list endpoint (new backend manager/`KiloBackendWorkspace`-style fetch + `KiloCliDataParser` mapping), new shared DTO + RPC method (workspace-scoped), consumed by the frontend.

**4. "Enable model routing" option** (Deluxe-only, gates ALL route UI — off/absent ⇒ no route UI anywhere): configurable route-capable provider-id list, **defaults `openrouter` + `openrouter-private`**, user-extendable for custom-URL OpenRouter providers; client-side id matching (no new provider-state DTO fields). Option storage in fork-owned code.

**5. Staleness handling**: a pinned route missing from the refreshed list is **NOT auto-cleared** — the pin stays active until the user explicitly re-picks (unfreeze required when locked); notice + refreshed route list (re-fetch on demand) make the recovery path visible. Accepted consequence: a dead pin (`allow_fallbacks: false`) fails requests hard until re-picked.

## Hard constraints

- **No `ModelStateDto`/`model.json` change**; selection unit stays `(providerID, modelID)`.
- Selection/variant/route writes go through the project parameter store (store ticket); this ticket consumes it.
- Upstream `ModelPicker`/`PickerPopup` internals untouched for routes.

## Verification

- Component/controller tests (route rows gated by the option; lock engages at first message; unfreeze flow; staleness keep-pin + notice); existing picker tests stay green; header binding swap covered by session UI tests.
<!-- SECTION:DESCRIPTION:END -->
