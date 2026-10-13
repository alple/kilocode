---
id: KILO-11
title: Hide Kilo Gateway models from picker Recommended section when not logged in
status: Testing
assignee:
  - dev1
created_date: '2026-10-13 11:09'
updated_date: '2026-10-13 11:24'
labels: []
dependencies: []
priority: medium
type: bug
ordinal: 34000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
User report (2026-10-13): the model picker's Recommended section lists Kilo Gateway models even when not logged into the gateway, and those entries skew the section. The kilo provider autoloads with an anonymous key for logged-out users (kilocode/provider/provider.ts kiloCustomLoaders), so the gateway catalog (cloud fetch or bundled models.dev fallback) still reaches pickers, and its models carry recommendedIndex (from the cloud API's preferredIndex, mapped in kilo-gateway/src/api/models.ts transformToModelDevFormat; the bundled fallback carries it too). Every picker's Recommended section filters on recommendedIndex != null (e.g. JetBrains ModelPickerRows.kt), so gateway models top the section and outrank the user's own providers.

Fix plan (single point, Kilo-owned file): in packages/opencode/src/provider/models.ts where providers.kilo is assembled, when token(opts, info) resolves to no credentials, strip recommendedIndex from the kilo catalog models (both fetched and fallback). token() already covers env var + auth store + config key, so no new service dependency is needed. Logged-in behavior is unchanged. All downstream surfaces (JetBrains picker, VS Code webview, agent-manager ordering, remote catalog) inherit the fix.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Kilo-provider models lose recommendedIndex when the user has no kilo credentials (no KILO_API_KEY env, no auth-store entry, no config apiKey) — verified by unit test
- [ ] #2 Models keep recommendedIndex when any kilo credential exists (env var, oauth/api auth entry, or config key)
- [ ] #3 Non-kilo providers and user-config-recommendedIndex are untouched
- [ ] #4 JetBrains picker Recommended section no longer lists gateway models when logged out (downstream effect; no JetBrains change needed)
- [ ] #5 bun run typecheck passes from packages/opencode and the new test passes
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-13 11:24
---
Committed and pushed per user request (2026-10-13): ed0efcdbea fix(cli) with changeset + 8-test suite. Also required f02901179a fix(jetbrains): the Gradle production gate rejected pinned=false for lux builds even though build-version.sh's shell check and the lux-counter commit (2fb61b2a34) document that exemption — lux5 would not build without it; real publish builds still require the pin. Built and pushed lux 7.1.9-lux5 (tag jetbrains-lux/v7.1.9-lux5 → f02901179a; ZIP kilo.jetbrains-7.1.9-lux5.zip, 153 MB, bundles the repo-built CLI incl. this fix). Counter advanced to 6 and committed (4129651850). User's own 'Add code' commit (48e0b2d34d, one .gitignore line) rode along on the branch push.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Fixed in packages/opencode/src/provider/models.ts (Kilo-owned file). Extracted the kilo catalog assembly into an exported pure function kiloModels({ creds, valid, org, fetched, fallback }) that (1) preserves the existing selection logic (invalid → no models; org-scoped → fetched even when empty; else fallback when fetch is empty) and (2) strips recommendedIndex from every model when creds is undefined. creds = token(opts, info), which already covers KILO_API_KEY env, auth-store oauth/api entries, and config apiKey — no new service dependency. get() now hoists the token() call into creds and calls kiloModels; logged-in behavior is byte-identical.

Verification: new test/kilocode/provider/models-catalog.test.ts (8 tests: logged-out strips ranking but keeps other fields; logged-in keeps ranking incl. fallback path; unranked models pass through by reference; org-pins-fetched and invalid-token behaviors preserved; empty catalog). All 8 pass; full test/kilocode/provider suite 81/81 pass; bun run typecheck green. Downstream surfaces (JetBrains ModelPickerRows Recommended section, VS Code webview, agent-manager ordering) inherit the fix — no JetBrains change needed. Not yet committed (previous commit cycle was user-directed; awaiting go-ahead). Ticket → Testing.
<!-- SECTION:FINAL_SUMMARY:END -->
