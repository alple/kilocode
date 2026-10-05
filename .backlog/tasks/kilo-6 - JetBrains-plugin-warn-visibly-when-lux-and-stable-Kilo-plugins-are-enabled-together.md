---
id: KILO-6
title: >-
  JetBrains plugin: warn visibly when lux and stable Kilo plugins are enabled
  together
status: To Do
assignee: []
created_date: '2026-10-05 15:40'
labels: []
dependencies: []
type: enhancement
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Installing a lux build from disk while the stable "Kilo Code" plugin is still enabled silently breaks the running IDE: IntelliJ dynamically loads the lux plugin, every Kilo.* action registration fails with SEVERE "ID already taken" (including Kilo.Session.Rollback), and the two plugins' same-FQN classes produce a ClassCastException storm across client↔backend RPC (permission replies, telemetry, workspace service, session board). The user sees the old stable frontend with no explanation — this is how KILO-5's rewind work appeared to vanish after installing 7.1.8-lux1 on 2026-10-05 (diagnosed in-session from idea.log/kilo.log evidence).

KILO-2 documented the constraint ("keep exactly one of lux/stable enabled at a time") but nothing enforces or surfaces it. Add a guard: when a Kilo plugin instance starts (or is dynamically loaded) while the other identity is also enabled, show a visible warning notification naming both plugins and instructing the user to disable one and restart.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 When a Kilo JetBrains plugin instance starts while the other identity (ai.kilocode.jetbrains vs ai.kilocode.jetbrains.lux) is also enabled in the same IDE, a user-visible warning notification names both plugins and instructs to disable one and restart — silent action-ID collision / ClassCastException breakage is never the only signal.
- [ ] #2 #2 The guard is direction-agnostic: fires when lux coexists with enabled stable and when stable coexists with enabled lux.
- [ ] #3 #3 No warning when exactly one Kilo plugin identity is enabled (the supported state).
- [ ] #4 #4 Detection survives dynamic plugin load (install-from-disk while the other instance is running), not just clean startup.
- [ ] #5 #5 JetBrains typecheck and tests pass; UI follows the jetbrains-ui skill (notification via existing Kilo notification group, strings in the base bundle).
<!-- AC:END -->
