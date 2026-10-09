---
id: KILO-9
title: >-
  JetBrains: favorites management — named model, route and effort presets with
  labels
status: To Do
assignee: []
created_date: '2026-10-09 09:27'
updated_date: '2026-10-09 09:35'
labels:
  - 'wayfinder:map'
dependencies: []
priority: high
type: epic
ordinal: 27000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Destination

A decided design (ADR + spec) for favorites management in the JetBrains plugin: a dedicated management window where a favorite is a **named combination of model + OpenRouter route + reasoning effort**, carrying user-assigned **labels**, where the labels decide which favorites the session parameter picker shows; the session popup surface (restored or cloned old popup) displays the labeled favorites. Done = design settled and build tickets seeded.

## Notes

Domain: JetBrains plugin chat session header. Current favorites are flat `(provider, model)` pairs surfaced by the app (`app.favorites` / `toggleModelFavorite`, `ModelSelectionDto`). The Deluxe parameter picker (KILO-8.4, ADR-0001) already composes model + route + effort in the session header, with the upstream `ModelPicker` popup still bound but hidden since the header swap.

Constraints and conventions to respect: fork-owned Deluxe surfaces in `ai.kilocode.client.deluxe` per ADR-0001 (upstream `ModelPicker`/`PickerPopup` internals untouched for new features; session-header-scale upstream edits only); per-project parameter store (ADR-0002) is the JetBrains write target; no new shared provider-state DTO fields; Swing + IntelliJ platform components only (no Kotlin UI DSL v2, Compose, or JCEF); user-visible strings in bundle properties. Route data comes from the CLI `/kilo/routes/{author}/{slug}` proxy (uptime/pricing/quantization per endpoint).

Skills: grilling + domain-modeling for decision tickets; jetbrains-ui / jetbrains-session / jetbrains-arch for UI and architecture grounding.

## Decisions so far

(empty — one line per closed ticket: title link + one-line gist)

## Not yet specified

Implementation slicing (build tickets) — seeded by the design ticket once the design lands; anything the design pass leaves unresolved graduates into fresh tickets.

## Out of scope

(none yet)
<!-- SECTION:DESCRIPTION:END -->
