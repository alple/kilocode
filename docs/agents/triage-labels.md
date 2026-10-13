# Triage Labels

> **Source of truth: the tracker is [Backlog.md](backlog.md)** — triage state is Backlog **statuses** + **labels**, not file status lines. **Board statuses are not wayfinder statuses** — roles ride as labels. This file maps the triage roles the skills still speak onto the live tracker (repo docs win over skills).

## Mapping skill triage roles → Backlog.md

| Legacy role (skills) | Backlog.md | Meaning |
|---|---|---|
| `needs-triage` / `needs-info` | Status `Backlog` + label `needs-triage` | Fresh report: brief recorded, triage/grilling pass pending. Not current work; triage sorts: soon → `To Do`, later → stays `Backlog` |
| Status `Draft` | **Explicit developer request only** | The developer says "draft" when collecting a vague idea; agents never send reports to Draft on their own |
| `ready-for-agent` | Status `To Do` + label `ready` | Fully specified, takeable by an AFK agent |
| `ready-for-human` | The **`Testing` status** | Agent-finished, awaiting the developer's test/review = move the ticket to `Testing`; never self-`Done` (backlog.md → "Ticket flow"). A `ready-for-human` **label** is a legacy record only |
| `wontfix` | Do **not** action; record `WONTFIX` in the task description (Done tasks cannot be archived — the description is the record) | Will not be actioned |
| `implemented` / `resolved` / `done` | Status `Done` (+ `finalSummary` when the work itself is done in-session) | Finished — **legacy records only**; live work never self-marks `Done` (Ticket flow) |
| `split` | Parent task carrying subtasks; parent mirrors children | Superseded by numbered follow-ups |

## Live label vocabulary

Workflow state only, kept small: `needs-triage`, `ready`. The **`Testing` status** (not a label) is the live agent-finished/awaiting-review signal. The cross-cutting label `testing` describes *work kind* (QA/test work), unrelated to the `Testing` *status*.

- **Labels are not the category** — the task **type** (`bug`, `feature`, `enhancement`, `chore`, `docs`, `spike`, `task`, `epic`, `initiative`) is the only category marker; no `bug`/`feature` labels duplicating it. Cross-cuts `refactoring`, `testing`, `docs` stay available when they add meaning beyond the type (e.g. a bug whose fix is a repo-wide refactor).
- **Time-aware labels** (definitions: backlog.md → "Time-gated & recurring tickets"): `time-gated` = the ticket waits for a gate (anchor event + wait, or a hard date) and carries exactly one `Time-gate:` line in its description; `recurring` = the series container (an initiative) of a repeating duty, carrying one `Recurrence:` line; series instances are `time-gated` subtasks with stamped hard due dates.
- **`epic`** marks spec-carrying feature roots; **`initiative`** marks specless long-running containers (both in `.backlog/config.yml` `types` — parent kinds: backlog.md → "Two kinds of parents").
- **`quick-fix`** — see below.

### `quick-fix` — size/blast-radius cross-cut

`quick-fix` marks small, contained, low-risk fix-ups ("quick refreshments": test flakes, copy/styling touch-ups, minor cleanup). A **size cross-cut** like `refactoring`/`testing`/`docs` — composes with any type (`bug` + `quick-fix`), never replaces the type.

**Auto-apply rule:** add `quick-fix` automatically — without asking — when ALL hold:

1. **Local change:** few files, one module/area; no cross-component wiring.
2. **Low blast radius:** cannot impact the service as a whole — no schema/migrations, no public API or event contract changes, no shared infrastructure.
3. **Low regression risk:** a new bug is unlikely — behavior unchanged, or test/fixture/doc/copy only.

Any check fails → don't label it (triage normally; it may still be small, but it isn't a fix-up). `quick-fix` never overrides the task lifecycle: agent-finished work still moves to `Testing`, never self-`Done`s; `Done` remains the developer's call.
