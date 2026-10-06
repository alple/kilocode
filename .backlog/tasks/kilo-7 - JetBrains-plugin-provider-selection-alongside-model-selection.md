---
id: KILO-7
title: 'JetBrains plugin: provider selection alongside model selection'
status: To Do
assignee: []
created_date: '2026-10-06 07:27'
labels:
  - 'wayfinder:map'
dependencies: []
priority: medium
type: epic
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Destination

A **decided design** (not an implementation) for choosing *which provider* to use in addition to *which model* in the Kilo **JetBrains plugin**: the UX intent settled, the picker shape chosen (extend / duplicate / new), and the required data-contract surface (RPC/DTO/CLI) named — enough to hand off to implementation planning.

## Notes

- Scope: JetBrains plugin (`packages/kilo-jetbrains/`) first; other surfaces (CLI TUI, VS Code Agent Manager) are nice-to-have and ride as fog.
- Key research fact already established: `packages/kilo-jetbrains/` is entirely Kilo-owned (no opencode upstream counterpart), so the fork **upstream-conflict concern does not apply here** the way it does in the TUI. The real constraints are (a) churn in shared client-UI code patterns, (b) stability of the RPC DTO contract with the CLI (`shared/.../rpc/dto`), (c) consistency with other Kilo surfaces.
- Current state (verified in code): the JetBrains model picker already groups models under provider sections, search matches provider names, button labels show `Provider / Model` for non-Kilo providers, and every selection is a `provider/id` pair. Provider *connect/disconnect/auth* lives in Settings → Providers. The picker only lists the Kilo provider + already-connected providers.
- Work sessions: load `jetbrains-ui` / `jetbrains-session` / `jetbrains-arch` skills as needed; domain vocabulary in `GLOSSARY.md`, ADRs in `docs/adr/` when decisions deserve one.
- Tickets are decisions only — resolving a ticket closes it; implementation planning happens after the map is clear.

## Decisions so far

## Not yet specified

- Whether the decided pattern should carry over to the CLI TUI and VS Code Agent Manager (nice-to-have per user).
- Whether selecting a currently-disconnected provider from the picker (with inline connect) is in scope.
- Whether sub-provider routing choice (model-level routing for Kilo Gateway models) is part of "provider choice".
- Per-agent / per-worktree default provider+model persistence behavior.

## Out of scope
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Map has Destination, Notes, Decisions so far, Not yet specified, Out of scope sections
- [ ] #2 All child decision tickets created with wayfinder:* labels and blocking edges wired
- [ ] #3 Each resolved ticket lands a resolution comment + a Decisions-so-far pointer on this map
<!-- AC:END -->
