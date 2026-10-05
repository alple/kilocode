---
id: KILO-6
title: >-
  JetBrains plugin: warn visibly when lux and stable Kilo plugins are enabled
  together
status: To Do
assignee: []
created_date: '2026-10-05 15:40'
updated_date: '2026-10-05 16:31'
labels: []
dependencies: []
type: enhancement
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Installing a lux build from disk while the stable "Kilo Code" plugin is still enabled silently breaks the running IDE: IntelliJ dynamically loads the lux plugin, every Kilo.* action registration fails with SEVERE "ID already taken" (including Kilo.Session.Rollback), and the two plugins' same-FQN classes produce a ClassCastException storm across client↔backend RPC (permission replies, telemetry, workspace service, session board). The user sees the old stable frontend with no explanation — this is how KILO-5's rewind work appeared to vanish after installing 7.1.8-lux1 on 2026-10-05 (diagnosed in-session from idea.log/kilo.log evidence).

KILO-2 documented the constraint ("keep exactly one of lux/stable enabled at a time") but nothing enforces or surfaces it. Scope (developer-approved 2026-10-05): auto-disable, not just warn.

- Detect at startup and on dynamic plugin load: scan PluginManagerCore plugins for an enabled Kilo identity other than our own (ai.kilocode.jetbrains stable ↔ ai.kilocode.jetbrains.lux).
- Auto-disable the other identity (PluginManagerCore.disablePlugins → disabled_plugins.txt, effective on restart, fully reversible) and show a notification naming both plugins with a Restart action. The conflict never gets a second loaded classloader, so the collision class dies entirely.
- Explicitly out of scope (decided): auto-uninstall of the other identity — destructive and surprising if stable is the daily driver; revisit only if disable proves insufficient. Same-plugin-id upgrades (lux1 → lux2) replace in place and are not a conflict; the guard only ever acts on the cross-identity case.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 When a Kilo JetBrains plugin instance starts while the other identity (ai.kilocode.jetbrains vs ai.kilocode.jetbrains.lux) is also enabled in the same IDE, a user-visible warning notification names both plugins and instructs to disable one and restart — silent action-ID collision / ClassCastException breakage is never the only signal.
- [ ] #2 #2 The guard is direction-agnostic: fires when lux coexists with enabled stable and when stable coexists with enabled lux.
- [ ] #3 #3 No warning when exactly one Kilo plugin identity is enabled (the supported state).
- [ ] #4 #4 Detection survives dynamic plugin load (install-from-disk while the other instance is running), not just clean startup.
- [ ] #5 #5 JetBrains typecheck and tests pass; UI follows the jetbrains-ui skill (notification via existing Kilo notification group, strings in the base bundle).
<!-- AC:END -->
