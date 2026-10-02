# Issue tracker: Backlog.md

> **Source of truth: the tracker is [Backlog.md](backlog.md) via the `backlog` MCP server (data in `.backlog/`, a symlink to the `issue-tracker` branch's worktree).** All new issues, epics, milestones, and comments go there. This file only maps legacy skill vocabulary onto the live tracker.

## Where skills' tracker instructions map now

Skills still speak in legacy local-markdown terms (feature directories, buckets, `Status:` lines, `spec.md`, wayfinder maps). Repo docs win over skills — interpret them through this table:

| Skill instruction | Do this instead |
|---|---|
| "Publish to the issue tracker" / create a feature dir | Create Backlog.md task(s) — feature root (type `epic`, spec-carrying) + subtasks as needed; `initiative` for specless long-running containers (backlog.md → "Ticket flow") |
| "Fetch the relevant ticket" | `task_view` / `task_search` on the Backlog task |
| Triage role labels (`needs-triage`, `ready-for-agent`, …) | Map through `docs/agents/triage-labels.md` (statuses + labels in Backlog) |
| Wayfinder map/child tickets | Backlog feature root (`wayfinder:map` label) + child tasks — see "Wayfinding operations" below |
| Wayfinder `ready-for-agent` / `ready-for-human` | Labels, not board statuses: `ready` (= ready-for-agent); ready-for-human = the **`Testing` status** |
| Legacy `.scratch/` paths (in old ADRs, docs, comments) | Never write to `.scratch/` — the tracker is Backlog.md, not a feature-dir tree |

**Task lifecycle:** canonical spec: backlog.md → "Ticket flow"; always-loaded gate summary: AGENTS.md → "Task lifecycle".

## Wayfinding operations

Used by `/wayfinder`:

- **Map**: a parent task labelled `wayfinder:map` — Destination / Notes / Decisions-so-far (as an index: one gist + link per closed decision) in the description.
- **Child ticket**: a subtask (`parentTaskId` = the map task), labelled `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`), with the question in the body.
- **Blocking**: native `dependencies` on the subtask. Unblocked when every dependency is Done.
- **Frontier**: open children whose dependencies are all Done; first in order wins. Soft frontier: blockers in `Testing`/`Done` count as satisfied for planning; the native `ready` filter stays the hard gate.
- **Claim**: assign the ticket before any work — the assignee is the claim.
- **Resolve**: resolution comment + description/notes update, a context pointer (gist + link) appended to the map's Decisions-so-far, and the subtask closed `Done` (map tickets are exempt from never-auto-Done).
