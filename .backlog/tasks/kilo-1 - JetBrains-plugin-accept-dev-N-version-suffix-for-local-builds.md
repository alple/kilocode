---
id: KILO-1
title: 'JetBrains plugin: accept -lux<N> version suffix for local builds'
status: Testing
assignee: []
created_date: '2026-10-02 19:12'
updated_date: '2026-10-02 19:31'
labels: []
dependencies: []
type: enhancement
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
`checked()` in packages/kilo-jetbrains/build.gradle.kts and the regex in packages/kilo-jetbrains/script/build-version.sh reject `-lux<N>` suffixes, so local builds like `7.1.8-lux1` cannot be built with the standard version script.

Extend both regexes to accept an optional `-lux<N>` suffix (SemVer pre-release, sorts below the base release — a lux build can never outrank a real release). Update the script's usage/help text accordingly.

Test ZIP: `script/build-version.sh 7.1.8-lux1 --skip-signing --skip-verification`.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Extended the version validation to accept an optional `-lux<N>` pre-release suffix (SemVer pre-release, sorts below its base release so a lux build can never outrank a published version):

- `packages/kilo-jetbrains/build.gradle.kts`: `checked()` regex is now `^[0-9]+\.[0-9]+\.[0-9]+(-rc\.[0-9]+)?(-lux[0-9]+)?(\+[0-9a-f]+)?$` with an updated comment.
- `packages/kilo-jetbrains/script/build-version.sh`: same regex added; usage text and examples updated to document `-lux<N>`.

Verified the regex by matrix (valid: 7.1.8, 7.1.8-rc.1, 7.1.8-lux1, 7.1.8-lux12, 7.1.8-rc.1-lux1, 7.1.8+sha, 7.1.8-lux1+sha; invalid: 7.1.8-lux, 7.1.8-luxx, 7.1.8-dev1, 7.1.8-1.2). An earlier `-dev<N>` spelling was replaced by `-lux<N>` per user decision; no `-dev` traces remain in the touched code.

Remaining: test build `script/build-version.sh 7.1.8-lux1 --skip-signing --skip-verification` (awaiting user go-ahead), then install from disk in the IDE.
<!-- SECTION:FINAL_SUMMARY:END -->
