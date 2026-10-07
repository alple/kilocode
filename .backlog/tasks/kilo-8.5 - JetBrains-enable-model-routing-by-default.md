---
id: KILO-8.5
title: 'JetBrains: enable model routing by default'
status: In Progress
assignee:
  - dev1
created_date: '2026-10-07 15:32'
updated_date: '2026-10-07 15:33'
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
- [ ] #1 A fresh install (no saved KiloRoutingOptions state) shows route UI for the default route-capable providers (openrouter, openrouter-private) without visiting settings
- [ ] #2 A stored enabled=false survives and still hides every piece of route UI (explicit opt-out intact)
- [ ] #3 KiloRoutingOptionsTest and KiloRoutingConfigurableTest cover the new default and stay green; jetbrains typecheck green
<!-- AC:END -->
