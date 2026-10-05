# Tracker: Backlog.md — capabilities, statuses, and the ticket flow

Backlog.md is this repo's task tracker, used through the `backlog` MCP server (data in `.backlog/`, a symlink to the `issue-tracker` branch's worktree — see [Tracker branch](#tracker-branch-issue-tracker)). CLI equivalent for the same tools: `npx backlog.md …` — never bare `npx backlog …` (that resolves to an unrelated third-party npm package). **This file is the canonical spec for statuses, grouping, organisation, and the ticket flow** — it wins over skill/resource suggestions, incl. the MCP's task-finalization guidance. Task lifecycle/execution discipline otherwise follow the MCP resources (`backlog://workflow/overview` + task-creation / task-execution / task-finalization).

> Re-verify this file's capability claims after major `backlog.md` bumps.

## Tracker branch (`issue-tracker`)

The tracker data does **not** live on any development branch. It lives on the `issue-tracker` branch — a **board-only orphan branch** holding exactly `.backlog/` and nothing else (no code snapshot, so nothing to drift), kept **local to this clone** (not pushed to a remote), **never merged** in either direction. The branch is the archive; ticket history is its own cadence and feature branches stay code-only.

Every checkout reads and writes the board through a **git worktree + symlink** (git follows the symlink, so writes land in the worktree where `issue-tracker` is checked out — commits made there never touch the feature branch):

```
<repo>/                                                        ← any development branch checked out
├── .backlog -> .kilo/worktrees/issue-tracker/.backlog         (symlink, gitignored)
└── .kilo/worktrees/issue-tracker/                             ← git worktree with issue-tracker checked out
```

Rules:

- **Never stage, commit, or edit `.backlog/` on a development branch or in a feature PR.** Development branches end with zero `.backlog/` changes. The symlink makes the board fully readable and writable from any branch — there is no reason to touch git state for it.
- **Board commits follow `auto_commit`** (`.backlog/config.yml` — the user flips it; backlog.md → "Board commits and pushes"): agents never push the board, and stale uncommitted board work a day or two old → **ask the user** whether to commit it.
- **The tracker worktree lives under `.kilo/worktrees/`** alongside the Agent Manager worktree pool. That is safe: the Agent Manager only flags/removes directories under `.kilo/worktrees/` that **no git worktree registration claims**, and the tracker worktree is a git-registered worktree (`git worktree list` shows it). `.kilo/.gitignore` (tracked) keeps `worktrees/` out of version control.
- A broken symlink (worktree missing) fails safe: the tools report "No Backlog.md project found" — nothing is written anywhere.

### Board commits and pushes

`auto_commit` in `.backlog/config.yml` decides whether the backlog CLI commits board edits itself — the user flips it on and off from time to time: never assume its value, never change it unasked, and treat whatever a CLI call committed as intended. Standing rules regardless of the setting:

- **Agents never push the board** — the `issue-tracker` branch is local to this clone by design; pushing it anywhere (e.g. to the public remote) is the user's explicit call, never an agent's.
- **Stale uncommitted board work:** `git status` in the worktree showing board changes a day or two old → **ask the user whether to commit the outstanding tickets**; never commit or discard them unasked.

Manual recipe (when `auto_commit` is off and the user asks): `git -C .kilo/worktrees/issue-tracker add -A && git commit -m "tracker: <what changed>"` (run with `-C .kilo/worktrees/issue-tracker`).

**No sentinel PR is possible**: `issue-tracker` is an orphan (no history in common with development branches) — a remote would not be able to open a PR for it even if it were pushed.

### New-environment setup (one-time per clone)

```bash
# 1. worktree holding the tracker branch
git worktree add .kilo/worktrees/issue-tracker issue-tracker

# 2. symlink the tracker data into the repo root
ln -s .kilo/worktrees/issue-tracker/.backlog .backlog

# 3. sanity check — the board should list tasks
npx backlog.md task list
```

The symlink is machine-local plumbing (gitignored) — it is not committed. If the clone has no local `issue-tracker` branch yet, the user provisions it (from a bundle/export or a fresh `config.yml`); agents never invent tracker history.

## Entities — one dimension per mechanism

| Question | Mechanism |
|---|---|
| *What kind* of work? | **Type** (`bug`, `feature`, …, `epic`, `initiative`) — the only category marker |
| *What workflow state?* | **Label** (wayfinder/triage roles, cross-cuts) |
| *How broken down?* | **Parent task + subtasks**, or separate tasks + dependencies |
| *When does it land?* | **Milestone** (sprint/release bucket) |
| *Where on the board?* | **Status** (`Backlog`, `To Do`, `In Progress`, `Testing`, `Done`) |

Board statuses are **not** wayfinder statuses — the legacy roles map through `docs/agents/triage-labels.md` (see [Ticket flow](#ticket-flow-the-statuses-in-motion)). Long-form intent (spec, PRD) → **document** (type `specification`), linked from the feature root's `documentation`/`references`.

## Verified capabilities

Probed against the CLI version pinned at setup time (re-run the probe if backlog.md changes materially):

- **Nesting:** subtasks ≥2 levels deep (`kilo-1 → kilo-1.1 → kilo-1.1.1`); dotted IDs (`kilo-N.M[.K]`); tree rendered via `Parent:`/`Subtasks:`. `parentTaskId` takes an existing **task** ID, never a milestone ID.
- **Labels:** colon hierarchies work (`group:refactoring` round-trips; filters via `task_list(labels=["group:refactoring"])`); string, max 50 chars.
- **Milestones:** unique name + optional due date (`YYYY-MM-DD`); filter via `task_list(milestone=...)`. Nothing auto-enforced — a grouping label.
- **Statuses:** user-defined — configured as `["Backlog", "To Do", "In Progress", "Testing", "Done"]`, `default_status: "Backlog"`. `task_list(ready: true)` = dependencies Done (probed: a blocked task drops out of `--ready` until its dependency reaches `Done`). `Draft` is not in the configured list but works as a hidden status (drafts live in `.backlog/drafts/`, excluded from normal listings) — only when the developer explicitly says "draft".
- **Terminal states:** `task_archive` refuses Done tasks — use `task complete` for terminal-status cleanup; a `WONTFIX` record lives in the task description.
- **`statuses` and `types` are config-only** — keys in `.backlog/config.yml`, settable like `labels` (edit the config file directly; no `config set`). **A long-running MCP server caches both lists at startup — restart the backlog MCP server after changing either**, or MCP writes/filters with the new value fail validation (CLI reads stay fine).

## Ticket flow (the statuses in motion)

Canonical lifecycle for every task. Board statuses are **not** wayfinder statuses — both legacy roles map through `docs/agents/triage-labels.md`.

### Status meanings

| Status | Meaning | Who moves it there |
|---|---|---|
| `Backlog` | Longer-term work, recorded reminders, fresh untriaged reports (`Backlog` + label `needs-triage`) — not current | Agent at creation (rule of thumb) |
| `To Do` | Current work done soon. Default landing for new tickets; fully specced and takeable → label `ready` | Agent at creation / when speccing |
| `In Progress` | Claimed and being worked | Agent, on start |
| `Testing` | Agent-finished, awaiting the developer's test/review — **replaces** the old `ready-for-human` label as the live signal | Agent, on finishing |
| `Done` | The developer tested and accepted | **The developer only — never automatic** |

`Draft` stays as before: only on the developer's explicit "draft" (a vague idea being collected) — agents never send reports to Draft.

### Creating a ticket

1. **Find the parent first.** Search for a suitable epic (feature root) — open **or closed**. Found → **ask: attach it there or create new?** Not found → standalone.
2. **Route escalation:** adjustment/follow-up tickets for one feature clustering as standalones → create a fresh **feature root** (type `epic`) carrying the spec, group them under it.
3. **Set status explicitly at creation** — never rely on defaults. Current work done soon → `To Do`; clearly future reminder → `Backlog`. **Uncertain → ask.** Fresh untriaged reports → `Backlog` + `needs-triage` (triage sorts: soon → `To Do`, later → stays).

### Two kinds of parents

- **Feature root (type `epic`):** carries the feature's **spec** (linked document or in-description); the route for the feature and its rounds of adjustments — each round a child ticket. It is itself a work item: moves through the flow and **mirrors its children** (below). Wayfinder maps hang off it (map = route epic + decision-ticket children with native `dependencies`).
- **Initiative (type `initiative`):** a specless **long-running container** for related but independently specced tasks (e.g. an SDK swap + facade migration + test repair + verification, each task its own spec). Status semantics **ignored**: sits in `To Do` for its whole life, excluded from mirroring and close-out, retired by hand (`task_archive`). Rule of thumb: **an `epic` must carry a spec; a parent with no spec of its own is an `initiative`.** *(Rationale: containers without an end state must not carry workflow semantics — the industry-standard epic-vs-initiative/theme distinction.)*

### Parent mirroring (feature roots only)

Root status = **least-advanced child stage, capped at `Testing`** (`To Do` < `In Progress` < `Testing` < `Done`): any child not started → root `To Do`; any child `In Progress` → root `In Progress`; all children past `In Progress` → root `Testing`; all `Done` → root stays `Testing` (close-out is the developer's call). Consequence: a fresh child on a `Done` root **reopens the root** — no manual resurrection of closed epics. Initiatives never mirror.

### Lifecycle

Speccing = move a `Backlog` ticket to `To Do` when (or while) the spec is done; start = claim (assign) + `In Progress` before any work; finish = **`Testing`** + completion summary (`finalSummary`, or a comment) — **never `Done`**, overriding the backlog MCP task-finalization guidance (repo docs > tools/skills). `Testing` → `Done` is the developer's; agents may **ask**, never move unilaterally.

- **Fixes after Testing:** fold back into the same ticket (`Testing` → `In Progress`, then finish → `Testing` again); extensive → **propose a next ticket** (same feature root); unclear → **ask: implement now or new ticket?**
- **Wayfinder decision tickets are exempt:** resolving a map ticket closes it (`Done`) — its deliverable is the recorded decision, shaped live in grilling. Implementation tickets off the map follow the normal flow.
- **Soft frontier:** a task whose blockers all sit in `Testing`/`Done` may be started — treat blockers as satisfied for planning (work complete, review/testing pending), note it builds on unreviewed work. The native `ready` filter still requires `Done`; that hard gate is the developer's.
- **Exceptions (history record-keeping, not live work):** `wontfix` records land as `Done` + `WONTFIX` in the description.

## Time-gated & recurring tickets

Time-aware work: two label-marked families that make "what's ready to be finished?" mechanically answerable. No new statuses, types, or fields — a label plus one structured line in the description.

### Time-gated tickets (label `time-gated`)

Work deliberately parked until its **gate** passes: an anchor event plus a wait, or a hard date. The gate never auto-executes anything — passing it releases a **viability check**, and pickup stays the developer's decision through the normal lifecycle.

One `Time-gate:` line in the description:

- `Time-gate: merge <Nd> after merge of <branch-or-PR-ref> — check: <condition>`
  Anchor = git log on the development branch (merge date). Default grade; fine for waits of a month or more.
- `Time-gate: hard <YYYY-MM-DD> — check: <condition>`
  One-shot absolute date; gets the same early warning as recurring deadlines.

Defaults: wait **14 days** when unstated; `merge` grade when the grade is unstated. `— check:` is the ticket's own viability condition (flag unused in code, dependency landed, no rollback), verified at gate time. If a future anchor needs a deploy/release event instead of a merge, define that grade here explicitly before using it — don't improvise anchors in tickets.

Status: `To Do` when specced (+ `ready` per the normal rules). The label is dropped once the gate is crossed (work starts) or the ticket leaves the flow.

### Recurring series (label `recurring`)

A repeating duty (e.g. secret rotation every 6 months) lives as an **initiative** (specless container, parked `To Do`, retired by hand) with:

- label `recurring`;
- one `Recurrence:` line: `Recurrence: every <period> from last completion; warn at max(14d, 10%)`;
- one stamped instance at a time — a **subtask** (`kilo-N.M`) with label `time-gated` and `Time-gate: hard <date>` (last completion + period, computed at stamping time). One pending instance is always visible: the board itself is the reminder.

Instance creation: when an execution finishes, the finishing agent immediately creates the next instance (due = today + period). The time sweep backstops: if stamping was missed, it creates the instance when it finds the series due or inside the warning window. The period is floating (from last completion), so a late rotation shifts the series — recorded dates never lie.

### The time sweep

Runs when the developer asks ("what's due?", "what's ready to be finished?", "what's coming up?") — and as a standing rule, an agent that reads or edits a gated ticket inside its warning window or overdue flags that in the response:

1. Fish out candidates: `task_list(labels=["time-gated"])`; plus `task_list(labels=["recurring"])` to gap-check the series.
2. Parse each gate/recurrence line; resolve the anchor: git log on the development branch (`merge`), the date itself (`hard`), the instance's stamped date (recurring).
3. Classify and report as a table:
   - **waiting for anchor** — the anchor event hasn't happened (say what's missing);
   - **not yet** — gate opens <date> (time left);
   - **⚠ warning window** — within max(14d, 10% of period) of a deadline date (one-shot `hard` gates and recurring due dates; a passing soak date is not a deadline, so relative `merge` gates get no early warning — they report *ripe* instead);
   - **due** — run the ticket's `check:` condition; report ripe / not viable yet, with evidence;
   - **OVERDUE** — past a deadline date.
4. Never auto-start work — the developer picks up via the normal lifecycle. Series gap: a `recurring` initiative with no pending instance gets its next instance created (backstop).

## Grouping conventions

- **Labels carry workflow state and themes** (`needs-triage`, `ready`, `ready-for-human` as a legacy mapping, cross-cuts `refactoring`/`tech-debt`/`docs`). Timing/status live in status and milestones, not labels.
- **Parents are the two kinds above** (`epic` = spec-carrying feature root; `initiative` = specless container). Keep the hierarchy shallow (children are work orders; grandchildren rare).
- **Roots hold the spec; sub-tickets do the work** — a root ticket (feature root `epic`, or a single-feature root like `kilo-5`) carries the decided spec in its description (decisions, acceptance criteria, links); implementation rides on **sub-tickets** (`kilo-N.M`, created via `parentTaskId`, one slice each). Spec changes go to the root, never the sub-ticket; a root that no longer needs its own work item stays open only until its children are finished (mirroring for `epic` roots; a simple root just reaches `Testing`/`Done` on its own work as usual).
- **Subtasks vs. separate tasks:** subtasks for tightly coupled work on the same component; separate tasks wired with `dependencies` (state what each provides) for independent cross-component work parallelizable across sessions.
- **Milestones are time buckets only** — one per sprint/release, tasks assigned when scheduled; mixes refactorings and features freely. **Anti-pattern:** a permanent theme-milestone ("Refactoring") — duplicates the label's job and pollutes sprint planning.
- **`Draft` = parked vague idea**, developer-explicit opt-in only (hidden from normal listings). Everything else parks in `Backlog`.
- **Sizing:** one task = one focused PR. Ten acceptance criteria = a task that wants splitting.

## Standing rule: never edit `.backlog/` markdown directly

All changes via MCP tools (or the `backlog` CLI) so IDs, relationships, and history stay consistent. Config exception: `statuses`/`types`/`labels` in `.backlog/config.yml` are edited directly (restart the MCP server after — see Verified capabilities).
