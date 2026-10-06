---
id: KILO-7.2
title: Settle what "choosing a provider" means in the JetBrains model picker
status: In Progress
assignee:
  - dev1
created_date: '2026-10-06 07:27'
updated_date: '2026-10-06 07:35'
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

What does "choose the provider in addition to the model" mean for the user in the JetBrains picker? The picker already pins provider+model together, groups models by provider section, and matches provider names in search — so the raw capability exists. What's missing is the *intent*. Candidates to weigh against the research findings (KILO-7.1):

- **Provider-first two-level picker**: pick a provider, then see only its models (analogous to the TUI's post-auth `DialogModel providerID` scoping).
- **Provider filter/navigation inside the current single popup** (e.g. provider chips/tabs above the list).
- **Choosing currently-disconnected providers from the picker** (with inline connect), not just `kilo` + connected ones.
- **Sub-provider routing choice** for Kilo Gateway models (the model-level routing/autoRouting field).

This is a HITL grilling session with the user; the decision lands as a resolution comment naming the chosen intent and what it explicitly excludes.
<!-- SECTION:DESCRIPTION:END -->
