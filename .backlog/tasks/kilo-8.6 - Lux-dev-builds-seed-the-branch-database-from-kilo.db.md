---
id: KILO-8.6
title: 'Lux dev builds: seed the branch database from kilo.db'
status: In Progress
assignee:
  - dev1
created_date: '2026-10-07 15:32'
updated_date: '2026-10-07 15:34'
labels: []
dependencies: []
parent_task_id: KILO-8
priority: high
type: task
ordinal: 26000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Repo-CLI builds (lux zips, sandbox runs) stamp their installation channel from the building branch, and non-release channels get their own kilo-<channel>.db. A lux build therefore starts with empty conversation history — the lux4 field test read this as a bug. Keep the isolation (the original kilo.db must stay untouched so the stable plugin never meets dev schema changes) but make testing see the real history by seeding the branch database from it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A script under packages/kilo-jetbrains/script/dev/ seeds kilo-<channel>.db from kilo.db using a consistent snapshot copy (VACUUM INTO); the original database and its WAL are never written
- [ ] #2 The script computes the channel exactly like the CLI build (current git branch, same character sanitization) and skips release channels (latest/beta/prod), which share kilo.db
- [ ] #3 Re-running replaces a stale branch database and removes stale -wal/-shm sidecars first
- [ ] #4 After seeding, the repo CLI (channel = current branch) lists the real sessions for the repo directory when pointed at the default data root
<!-- AC:END -->
