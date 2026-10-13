---
id: KILO-10
title: >-
  Sync Deluxe edition with Kilo upstream main (CLI v7.8.9, JetBrains
  v7.1.10-rc.2)
status: Testing
assignee:
  - dev1
created_date: '2026-10-13 09:22'
updated_date: '2026-10-13 10:54'
labels: []
dependencies: []
references:
  - docs/adr/0001-deluxe-owned-route-picker.md
  - >-
    .backlog/tasks/kilo-8.4 -
    JetBrains-Deluxe-parameter-component-—-picker-UI-routing-option-staleness.md
priority: high
type: task
ordinal: 33000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
User directive (2026-10-13, superseding the original merge plan): the Deluxe merge/conflict-revert loop was too finicky and no Deluxe features are preserved — hard reset my-features to the newest all-stable Kilo release tag and re-apply only the lux version mechanics. Base chosen: jetbrains/v7.1.9 (JB 7.1.9 stable + CLI 7.8.8 stable; the tag commit itself still says 7.1.9-rc.2, corrected to 7.1.9). Lux tooling re-applied: kilo.lux.version=5 counter (next build = 7.1.9-lux5), build-version.sh lux logic, build-lux.sh, build.gradle.kts lux plugin-id wiring, seed-branch-db.sh. Agent tooling/docs restored from the archive ref: AGENTS.md, GLOSSARY.md (user-requested), docs/agents, .agents, .kilo, script/upstream, community-triage. docs/adr/0001-0003 intentionally not restored (document dropped features; live only on archive/deluxe-my-features). End state left uncommitted for user review per explicit instruction; nothing pushed. The upstream pickup/picker window is original as-is on this base — no re-integration needed.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 my-features is reset to the stable jetbrains/v7.1.9 release commit (08981b3a30; CLI 7.8.8 + JB 7.1.9), with the prior Deluxe work preserved on archive/deluxe-my-features
- [ ] #2 Version fixes applied: gradle.properties reads kilo.jetbrains.version=7.1.9 (tag's -rc.2 string corrected), kilo.lux.version=5 counter preserved, kilo.cli.pinned=false for local lux builds
- [ ] #3 Lux build tooling restored and syntax-checked: build-version.sh lux logic, build-lux.sh, build.gradle.kts lux plugin-id wiring, script/dev/seed-branch-db.sh
- [ ] #4 Agent tooling/docs restored from the archive ref: AGENTS.md, GLOSSARY.md, docs/agents, .agents, .kilo, script/upstream, community-triage files; docs/adr 0001-0003 intentionally not restored (document dropped features)
- [ ] #5 End state left uncommitted for user review per explicit instruction; nothing pushed
- [ ] #6 JetBrains gradle typecheck passes; CLI sources untouched from the released tag
- [ ] #7 Ticket moved to Testing with this summary; never auto-Done
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Base = jetbrains/v7.1.9 tag (user chose release tags over upstream/main; v7.8.8 CLI tag is contained in it; its gradle.properties says 7.1.9-rc.2 which the version fix corrects to 7.1.9).
2. Safety: archive/deluxe-my-features branch points at the pre-reset HEAD.
3. Reset: abort staged merge, git reset --hard jetbrains/v7.1.9.
4. Restore from archive ref: lux build bits (gradle.properties, build-version.sh, build-lux.sh, build.gradle.kts, seed-branch-db.sh) + agent tooling/docs (AGENTS.md, GLOSSARY.md, docs/agents, docs/community-triage.md, .agents, .kilo, script/upstream, community-triage) — staged, not committed (user instruction).
5. Not restored: docs/adr 0001-0003, all packages/* Deluxe source (parameter picker, preset store, routing, favorites, CLI route support).
6. Sync node_modules via bun install; discard bun.lock churn to keep the tag lock pristine.
7. Verify: bash -n both scripts; gradle.properties version state; JB gradlew typecheck.
8. Move ticket to Testing with summary; user reviews the staged state and commits (or asks me to).
<!-- SECTION:PLAN:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-13 10:54
---
Committed and pushed per user request (2026-10-13): commit 77cac6ce01 'chore: reset deluxe branch to Kilo 7.1.9/7.8.8 stable, restore lux tooling' on my-features (tag 08981b3a30 + restored tooling/docs/glossary, 100 files). Pushed with --force-with-lease (remote head was older at 2fb61b2a34). Also pushed archive/deluxe-my-features to origin so the prior Deluxe history is durable on the remote. Excluded from the commit per repo hygiene: .kilo/plans/1790923392937-openrouter-provider-routing-plan.md (left on disk), .backlog symlink and lux ZIPs (now .gitignored). Ticket remains Testing — Done is the developer's call.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Executed the user-approved reset instead of the conflict-prone merge. Steps: created archive/deluxe-my-features (full prior Deluxe work), aborted the staged upstream merge, hard-reset my-features to jetbrains/v7.1.9 (08981b3a30 — newest commit where both products are released stable: CLI 7.8.8, JB 7.1.9). Restored from the archive ref (100 staged files): lux build bits (gradle.properties with 7.1.9/-lux counter 5/pinned=false, build-version.sh lux logic, build-lux.sh, build.gradle.kts lux plugin id/name wiring, seed-branch-db.sh) and agent tooling/docs (AGENTS.md, GLOSSARY.md, docs/agents, docs/community-triage.md, .agents, .kilo, script/upstream, community-triage). deliberately not restored: docs/adr/0001-0003 (document dropped Deluxe features) and all packages/* Deluxe source. Verified: bash -n on both scripts OK; gradle.properties/version state confirmed; JetBrains ./gradlew typecheck EXIT=0; CLI untouched from released tag. bun.lock churn from install discarded (tag lock kept pristine). State left UNCOMMITTED for user review (100 staged files + untracked .backlog symlink/.kilo/worktrees/old lux ZIPs). Nothing pushed. Next lux build: script/build-version.sh 7.1.9-lux -> 7.1.9-lux5.
<!-- SECTION:FINAL_SUMMARY:END -->
