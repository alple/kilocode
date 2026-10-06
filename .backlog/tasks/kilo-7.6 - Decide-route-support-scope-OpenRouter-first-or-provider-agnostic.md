---
id: KILO-7.6
title: 'Decide route-support scope: OpenRouter-first or provider-agnostic'
status: To Do
assignee: []
created_date: '2026-10-06 09:19'
labels:
  - 'wayfinder:grilling'
dependencies:
  - KILO-7.5
parent_task_id: KILO-7
priority: medium
type: enhancement
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Question

KILO-7.5 established OpenRouter's dedicated endpoints sub-resource (`/api/v1/models/{author}/{slug}/endpoints`, public, rich server data). Other multi-server providers have different or no server-list APIs. What is the v1 scope for the route picker?

- **OpenRouter-only v1**: provider-agnostic DTO shape, but only OpenRouter has a live-list adapter; other providers show no route step (config-only kilo.json pinning still available for them).
- **Survey other providers first**: add a research ticket for e.g. LLM Gateway / Together / Fireworks server-list APIs before deciding.
- **Generic config-only for all, live list for OpenRouter**: the route picker reads a normalized route list the CLI assembles — live from OpenRouter, static from config for others.

Weigh against: user's actual provider (OpenRouter), DTO stability, and the Deluxe strategy (CLI endpoint changes are shared-code surface — `packages/opencode` is upstream-synced).
<!-- SECTION:DESCRIPTION:END -->
