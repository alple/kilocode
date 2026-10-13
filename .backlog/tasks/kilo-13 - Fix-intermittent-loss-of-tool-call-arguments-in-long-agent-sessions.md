---
id: KILO-13
title: Fix intermittent loss of tool-call arguments in long agent sessions
status: Backlog
assignee: []
created_date: '2026-10-13 18:19'
labels:
  - needs-triage
dependencies: []
type: bug
ordinal: 36000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Symptom

In one long agent session (order-service repo checkout, roughly 100 tool calls in), tool calls to two different MCP servers lost arguments intermittently. The loss sits between the model tool-call emission and the MCP server: flat string arguments arrive, nested object arguments and later-position arguments arrive empty or missing. Worse, creates often still SUCCEED, so the data loss is silent - a ticket created without dependencies, parent, or status looks normal until someone opens it.

Environment: Kilo Agent Manager session; model `z-ai/glm-5.3-flash` via `openrouter`; servers: `atlassian` (remote) and `backlog` (local Backlog.md MCP).

## Evidence

### A. atlassian editJiraIssue - object argument dropped (6 attempts across 2 sessions)

Intended arguments (representative attempt):

```json
{
  "cloudId": "cf34bc3a-... (truncated)",
  "issueIdOrKey": "CATS-683",
  "actionSource": "model_tool_call",
  "fields": {"parent": "CATS-11", "description": "<3-paragraph markdown, ~700 chars>"}
}
```

Server response, identical every time:

```json
{"error":true,"message":"Provide at least one field to edit, or rankBeforeIssue / rankAfterIssue to reorder the issue on a board."}
```

The flat string arguments (cloudId, issueIdOrKey) clearly arrived - otherwise the error would be about a missing issue - while `fields` arrived empty or absent. Tried: `fields` with 2 keys (x2), `additional_fields` with 2 keys, minimal `fields` with the single key `{"parent":"CATS-11"}` (no newlines), then the same two shapes again in a fresh session. All six failed identically.

### B. Control on the same server - flat string succeeds

`atlassian_addOrEditJiraIssueComment` with a ~700-char flat string `commentBody` succeeded (comment created). Same payload size, flat vs nested: flat passes, nested does not.

### C. atlassian createJiraIssue - the object param SURVIVED here

Emitted: summary, projectKey, issueType, `additional_fields: {"components":[{"name":"Backend"}]}` (description and parent were omitted in emission - recorded for attribution honesty). Result: success; server state verified later: component applied, description empty, no parent.

This falsifies a hard objects-always-drop rule: `additional_fields` survived once while `fields` never did. The loss is intermittent, not structural.

### D. backlog task_create - progressive degradation within one session

1. Early parallel batch (3 calls; title + description + acceptanceCriteria[3] + references[2]): all parameters landed.
2. Parallel batch (5 calls; same shape plus parentTaskId): all landed, parent included.
3. Parallel batch (2 calls; intended plus parentTaskId and dependencies): parentTaskId and dependencies missing in the created files; description and AC landed - bare tasks, had to archive and recreate.
4. Solo retry (title + description + AC[7] + parentTaskId + dependencies[5] + status): landed everything EXCEPT description - the created task said "No description provided".
5. Solo retries x4 (intended full payload, then a minimal parentTaskId+title form): only the title landed, every time, even the 2-param minimal form.
6. Control - the same operations via CLI in bash (`npx -y backlog.md task create --parent <id> -s "To Do" --dep <ids> -d <700 chars> --ac ... --ref ...`): 100% fidelity, three tasks created perfectly.
7. task_edit (description+status; dependencies+status; references): 5 of 5 landed.
8. One parallel task_edit batch: one call carried a WRONG task id (composed for one id, the server error named a different one) - argument corruption, not only truncation.

## Pattern

- Later arguments in a call drop more often than earlier ones.
- Nested objects (Jira `fields`) drop; same-size flat strings pass - but one object (additional_fields) did survive once, so it is probabilistic, not a hard rule.
- Degradation correlates with session length: early-session parallel calls complete; late-session solo calls collapse to a single argument.

## Ask

Check the tool-call path in the client for argument truncation or loss: how the model tool-call text is parsed into arguments (provider glm-5.3-flash via openrouter emits the calls; suspect serialization or parsing of nested JSON arguments), and whether size or position limits apply. Server-side logs on both MCP servers can be diffed against the emitted calls to localize the drop.

## Related (separate defect, found during the same investigation)

`atlassian_discover` results reference `executeWrite` and `executeDestructive` executors for write operations, but the agent toolset only exposed `executeRead` - write-tier operations were unreachable (this is what blocked linking a Jira ticket to its epic).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Multi-argument MCP tool calls, nested objects included, reach servers complete in long agent sessions
- [ ] #2 Root cause documented: which layer drops or truncates arguments, and under which conditions
<!-- AC:END -->
