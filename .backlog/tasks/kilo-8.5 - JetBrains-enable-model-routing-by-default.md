---
id: KILO-8.5
title: 'JetBrains: enable model routing by default'
status: Testing
assignee:
  - dev1
created_date: '2026-10-07 15:32'
updated_date: '2026-10-07 15:38'
labels: []
dependencies: []
parent_task_id: KILO-8
priority: high
type: enhancement
ordinal: 25000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The lux4 field test reported "no sign of routing": the gate defaulted to off, the user never found the settings page, and concluded the feature was missing. Route UI is the headline of the KILO-8 effort — hiding it behind an opt-in setting defeats it. Flip the default to on; an explicit off must keep hiding all route UI.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A fresh install (no saved KiloRoutingOptions state) shows route UI for the default route-capable providers (openrouter, openrouter-private) without visiting settings
- [x] #2 A stored enabled=false survives and still hides every piece of route UI (explicit opt-out intact)
- [x] #3 KiloRoutingOptionsTest and KiloRoutingConfigurableTest cover the new default and stay green; jetbrains typecheck green
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Flipped the Deluxe routing gate default to on (KILO-8.5). `KiloRoutingOptions.State.enabled` now defaults true with the class doc updated; a stored enabled=false still survives loadState (explicit opt-out intact). Tests: KiloRoutingOptionsTest defaults test renamed/asserts on-by-default for the OpenRouter pair, new "a stored off survives loadState" case; KiloRoutingConfigurableTest toggle test now pins the option state explicitly instead of relying on the old default. Changeset line updated in .changeset/jetbrains-deluxe-parameter-picker.md. Verification: `./gradlew typecheck` green; `:frontend:test` for KiloRoutingOptionsTest + KiloRoutingConfigurableTest green. Sandbox retest of the visible route rows recommended before Done (moved to Testing per lifecycle).
<!-- SECTION:FINAL_SUMMARY:END -->
