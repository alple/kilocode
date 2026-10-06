---
id: KILO-7.2
title: Settle what "choosing a provider" means in the JetBrains model picker
status: In Progress
assignee:
  - dev1
created_date: '2026-10-06 07:27'
updated_date: '2026-10-06 07:40'
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

What does "choose the provider in addition to the model" mean for the user in the JetBrains picker — settled intent: **routing a given model through a chosen provider** (e.g. glm-5.3-flash served by OpenRouter instead of the default provider). The routing capability exists mechanically (`(providerID, modelID)` rows → `selectModel` → per-turn `PromptDto`), so the decision is about what blocks it in practice and what the UX must expose:

- **Disambiguation**: when the same model is offered by 2+ providers, rows render identically (model name only; provider label only on favorites; Recommended section doesn't name its provider). How should duplicates be shown?
- **Reachability**: did the user's OpenRouter models even appear in their picker? (runtime fact from the user; if not, is the filter wrong or the connect state?)
- **Selection affordance**: is picking the right row enough, or does the user want an explicit provider step/filter inside the popup?

This is a HITL grilling session with the user; the decision lands as a resolution comment naming the chosen intent and what it explicitly excludes.
<!-- SECTION:DESCRIPTION:END -->
