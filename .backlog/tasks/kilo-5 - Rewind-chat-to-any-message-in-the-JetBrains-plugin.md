---
id: KILO-5
title: Rewind chat to any message in the JetBrains plugin
status: Testing
assignee:
  - alek
created_date: '2026-10-05 11:22'
updated_date: '2026-10-05 13:48'
labels:
  - ready
milestone: Chat rewind
dependencies: []
documentation:
  - docs/agents/triage-labels.md
  - docs/agents/backlog.md
  - packages/kilo-jetbrains/AGENTS.md
  - >-
    packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/KiloSessionRpcApi.kt
  - >-
    packages/kilo-jetbrains/shared/src/main/kotlin/ai/kilocode/rpc/dto/SessionDto.kt
  - >-
    packages/kilo-jetbrains/backend/src/main/kotlin/ai/kilocode/backend/cli/KiloCliDataParser.kt
  - packages/opencode/src/session/revert.ts
  - packages/kilo-vscode/webview-ui/src/components/chat/TranscriptRow.tsx
  - packages/kilo-vscode/webview-ui/src/components/chat/RevertBanner.tsx
type: enhancement
ordinal: 5000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cleaning up irrelevant turns in a long chat pollutes the model context: old experiments, dead-end explorations, and failed attempts keep eating tokens and confuse the assistant. The CLI already has the right machinery — a revert boundary set in storage (non-destructive, restorable via unrevert until the next prompt commits it) that hides messages past the boundary and drops them from the model context — but the affordances are limited: TUI /undo only rewinds to the previous user message, and the JetBrains plugin exposes revert only per answered message with no restore-surface parity. This ticket adds a first-class 'rewind chat to any message' affordance in the Kilo JetBrains plugin over the existing machinery, chosen deliberately to minimize divergence from upstream opencode (no shared revert/session code needs changing). Decisions locked with the developer: (1) semantics = existing revert machinery unchanged, including snapshot-based file restoration of the removed turns; (2) rewind boundary = any message, not just user messages; (3) primary surface = JetBrains plugin (user does not care about TUI/VS Code; other surfaces only if nearly free); (4) removed messages stay restorable until the next prompt in that session. Baseline: this work builds on the freshly merged upstream/dev state (merge commit df678b7299, fix-ups 18dfe2953a on branch my-features). Known environment noise, not this ticket's problem: 6 pre-existing CLI test failures (umask write test, 4 concurrent plugin-install tests, wakeup-tool registry test) confirmed identical at the pre-merge base, and a JetBrains Gradle failure where two long test names exceed the 255-char class-file path limit on deep checkouts.

This ticket is the spec root: the description above holds the decided spec; implementation slices live in sub-tickets below (tracker rule in docs/agents/backlog.md → "Roots hold the spec; sub-tickets do the work").

Sub-tickets:
- KILO-5.1 — Close KILO-5 rewind test gaps: rollback settle in SessionUi and queued publication (covers the KILO-5 final summary "Known gaps" items 1-2 plus a manual split-mode sandbox check).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 On any message in the JetBrains chat transcript (user, assistant, or tool-result message), a message action offers revert-to-this-message: everything after it is hidden from the transcript and dropped from the model context, and the reverted turn's code edits are restored from snapshots (existing revert semantics).
- [ ] #2 The rewind is restorable: a visible revert state (banner/indicator) exposes restore of the hidden messages back into the chat; restoring is possible any time before the user sends the next prompt in that session (the next prompt commits the rewind, after which the hidden messages no longer come back).
- [ ] #3 Only idle sessions can be rewound: the action is disabled/absent while a prompt is running, matching the existing revert guard.
- [ ] #4 Reverting to any message leaves the session in a state where the next prompt continues from the boundary message with no dangling tool-call, compaction-reference, or part errors (spot-check sessions with tool calls and a compaction summary before the boundary).
- [ ] #5 JetBrains plugin typecheck and tests pass on Linux with the Java 21 toolchain; new UI code follows the jetbrains-ui and jetbrains-arch skills.
- [ ] #6 If SDK or RPC shapes change on the CLI side, regenerated artifacts are updated via the documented generate script, not hand-edited.
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Rewind chat to any message in the JetBrains plugin — implemented.

**What changed (frontend only; CLI/RPC shapes untouched — AC #6):**

AC #1 — any message:
- Assistant text parts now carry a "Rollback to this message" button on their existing hover copy toolbar (`TextView`/`ViewFactory` gained a `rollback` callback threaded from `MessageView`; user bubbles keep theirs on the bubble toolbar).
- New `Kilo.Session.Rollback` context-menu action (`MessageRollbackAction`) works uniformly on any message kind — user, assistant, tool-result, including tool cards and code blocks — via a `kilo.session.message` data key published by `MessageView` (now a `UiDataProvider`); `DataManager`'s ancestor merge keeps `SessionActions` resolvable from these deeper providers.
- The restore surface (banner: count, per-file diff stats, Redo/Redo All, hint, workspace notices) already existed and satisfies AC #2.

AC #3 — idle-only rewind (decided with the developer: gate ALL rollback affordances):
- `SessionController.revert/redoTo/unrevert` now refuse while `state.isBusy()` instead of aborting the run first; the CLI's own `assertNotBusy` guard is never hit.
- Hover-toolbar rollback buttons disable with a "wait for the current prompt" tooltip while busy/reverting (`MessageToolbar.setRollbackEnabled`, fanned out from `SessionMessageListPanel` state sync and applied to fresh views on register).
- RevertBanner Redo/Redo All disable while busy; context-menu item hides while busy, in read-only hosts, and on queued prompts.

AC #4 — no dangling state: CLI machinery unchanged; controller spot-check test covers a rollback boundary before tool calls and a compaction (marker accepted, messages hidden, compaction count preserved, next prompt still sends). `onRevertChanged` now treats any resulting revert marker as settling a pending rollback, since the CLI widens an assistant/tool boundary to the preceding user message.

AC #5 — tests: controller busy-refusal tests (revert/redo/unrevert), rollback-to-any-kind round trip, tool+compaction spot check, panel tests for the disabled-while-busy affordances on both toolbars, context-menu action tests (visibility rules, per-message resolution, group order), updated bundle-parity expectations.

Strings added to the base bundle only (`revert.disabled.busy`, `action.Kilo.Session.Rollback.text/.description`); locales fall back to English via resource-bundle parents and the stale-key test stays green.

**Checks:** `./gradlew typecheck` green; full `:frontend:test` 4369 tests — all pass except the 2 pre-existing `TitleButtonTest` font-centering failures (verified failing on a clean tree in this environment); `:shared:test` + `:backend:test` green. Changeset added (`jetbrains-rollback-any-message.md`, kilo-code minor).

Note for local runs: `~` is eCryptfs and rejects class-file names > ~143 bytes; use `./gradlew -I /tmp/kilo/jetbrains-build-dir.init.gradle.kts ...` to relocate build dirs, and JDK 21 (`~/.sdkman/candidates/java/21-tem`).
<!-- SECTION:FINAL_SUMMARY:END -->
