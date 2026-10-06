---
id: KILO-7.2
title: Settle what "choosing a provider" means in the JetBrains model picker
status: In Progress
assignee:
  - dev1
created_date: '2026-10-06 07:27'
updated_date: '2026-10-06 07:47'
labels:
  - 'wayfinder:grilling'
dependencies:
  - KILO-7.1
parent_task_id: KILO-7
priority: high
type: enhancement
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

What does "choose the provider in addition to the model" mean for the user in the JetBrains picker — **settled: routing a given model to a chosen serving server inside its provider** (OpenRouter sub-providers like parasail, digitalocean for glm-5.3-flash). Remaining sub-decisions for this ticket:

- Confirm the intent excludes catalog-provider disambiguation (rows already carry that).
- Does the user prefer config-defined routes (kilo.json custom model entries, no code) or an in-picker route choice (needs CLI + JetBrains work), or config-first then UI?
- Persistence semantics: route as a default (per model/agent) vs per-session pick.

HITL grilling session with the user; the decision lands as a resolution comment naming the chosen intent and what it explicitly excludes.
<!-- SECTION:DESCRIPTION:END -->
