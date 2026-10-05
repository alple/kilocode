---
id: KILO-6
title: >-
  JetBrains plugin: warn visibly when lux and stable Kilo plugins are enabled
  together
status: In Progress
assignee: []
created_date: '2026-10-05 15:40'
updated_date: '2026-10-05 16:33'
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
- [ ] #1 #1 When a Kilo plugin identity starts (or is dynamically loaded) while the other identity is also enabled, the other identity is auto-disabled and a notification names both plugins with a Restart action — silent action-ID collision / ClassCastException breakage never happens.
- [ ] #2 #2 The guard is direction-agnostic: disables enabled stable when lux starts, and enabled lux when stable starts.
- [ ] #3 #3 No action when exactly one Kilo plugin identity is enabled (the supported state), and none for same-plugin-id replacement (lux1 → lux2 in-place upgrade).
- [ ] #4 #4 Disabling persists (disabled_plugins.txt) and the notification explains how to re-enable manually.
- [ ] #5 #5 JetBrains typecheck and tests pass; detection covers both clean startup and dynamic install-from-disk; strings in the base bundle; notification follows the jetbrains-ui skill.
<!-- AC:END -->
