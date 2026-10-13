---
id: KILO-11
title: Hide Kilo Gateway models from picker Recommended section when not logged in
status: To Do
assignee: []
created_date: '2026-10-13 11:09'
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
