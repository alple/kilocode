---
id: KILO-3
title: Hide Kilo Gateway from model pickers via disabled_providers config
status: To Do
assignee: []
created_date: '2026-10-05 10:11'
labels: []
dependencies: []
type: task
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
User wants Kilo Gateway ("kilo" provider) models out of the model pick list — it distracts from OpenRouter. Decision from discussion: hide entirely via existing config, zero code change, least upstream conflict.

Mechanism that already exists: `disabled_providers: ["kilo"]` in config (schema: packages/core/src/v1/config/config.ts; honored in packages/opencode/src/provider/provider.ts). This hides the gateway on every surface — user explicitly confirmed that is fine ("the rest is oblivious to me").

Work:
1. Add `disabled_providers: ["kilo"]` to the user's global Kilo config (~/.config/kilo/), not the repo.
2. Verify in the JetBrains plugin (packages/kilo-jetbrains/), which is the user's actual client:
   - model picker shows no Kilo Gateway models;
   - default model does not break if it referenced kilo/kilo-auto (extension settings default is kilo/kilo-auto/free in VS Code — check what JetBrains uses as default and how it behaves when the provider is absent);
   - settings/connect-provider list does not error.

Notes: hiding kilo also removes kilo-auto default models and the Auto/Recommended sections sourced from them. No code change expected; if verification reveals the JetBrains picker hardcodes gateway assumptions that error when absent, that becomes a follow-up fix ticket.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Kilo Gateway models no longer appear in the JetBrains plugin model picker after the config change
- [ ] #2 Default-model behavior degrades gracefully: if the previous default references a kilo/kilo-auto model, the plugin/CLI falls back to another provider's model without errors
- [ ] #3 Gateway remains hidden across restarts of kilo serve and across all surfaces (documented that this config hides it everywhere, including TUI and VS Code)
- [ ] #4 Config snippet applied to the user's global config (not committed to the repo)
<!-- AC:END -->
