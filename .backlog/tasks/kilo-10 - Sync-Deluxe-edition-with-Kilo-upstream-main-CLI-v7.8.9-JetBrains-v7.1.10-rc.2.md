---
id: KILO-10
title: >-
  Sync Deluxe edition with Kilo upstream main (CLI v7.8.9, JetBrains
  v7.1.10-rc.2)
status: To Do
assignee:
  - dev1
created_date: '2026-10-13 09:22'
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
User asked to bring the new Kilo version into the Deluxe edition before re-adding the Deluxe favorites windows (KILO-9.3/9.4 work continues separately). Kilo upstream (upstream remote = Kilo-Org/kilocode) main is 96 commits ahead of the merge-base defcd7dffb: CLI v7.8.9, JetBrains v7.1.10-rc.2, windows job-runner perf, glob-search-ignored, and a picker-area refactor (ModelPicker internals moved to new ModelPopup, PickerPopup churn) that collides with the Deluxe parameter-picker binding swap from KILO-8.4.

Recorded decision (user, 2026-10-13): restore the pickup window as in the original — resolve picker-area conflicts to the original/upstream side so the session header shows the stock model+reasoning pickers; the Deluxe model+route+effort picker goes dormant until the favorites windows return. Fork-owned Deluxe files carry over as-is; favorites windows stay deferred. lux/upstream-merge (opencode upstream/dev sync) is out of scope.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 my-features contains a merge commit of upstream/main (all 96 commits included) and `git rev-list --count my-features..upstream/main` is 0
- [ ] #2 Session-header picker shows the original/upstream model+reasoning pickers (new ModelPopup flow); the Deluxe parameter picker binding swap is removed from shared PromptPanel/SessionUi code
- [ ] #3 Fork-owned Deluxe sources (KiloParameterPicker, KiloFavorites*, KiloRouting*) still compile after the merge; anything that cannot compile is reported, not silently dropped
- [ ] #4 CLI: `bun run typecheck` and `bun test` pass from packages/opencode (pre-existing failures documented if any)
- [ ] #5 JetBrains: `./gradlew typecheck` and `./gradlew test` pass from packages/kilo-jetbrains (pre-existing failures documented if any)
- [ ] #6 `bun run script/check-opencode-annotations.ts --worktree` passes; kilocode_change markers in resolved shared files are intact
- [ ] #7 Nothing is pushed; the merge commit lands on my-features only
- [ ] #8 Ticket moved to Testing with a final summary listing conflict resolutions and verification results
<!-- AC:END -->
