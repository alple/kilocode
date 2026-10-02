---
id: KILO-1
title: 'JetBrains plugin: accept -dev<N> version suffix for local builds'
status: In Progress
assignee: []
created_date: '2026-10-02 19:12'
updated_date: '2026-10-02 19:12'
labels: []
dependencies: []
type: enhancement
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
`checked()` in packages/kilo-jetbrains/build.gradle.kts (line 30) and the regex in packages/kilo-jetbrains/script/build-version.sh (line 74) reject `-dev<N>` suffixes, so local dev builds like `7.1.8-dev1` cannot be built with the standard version script.

Extend both regexes to accept an optional `-dev<N>` suffix (SemVer pre-release, sorts below the base release — a dev build can never outrank a real release). Update the script's usage/help text accordingly.

Build a test ZIP: `script/build-version.sh 7.1.8-dev1 --skip-signing --skip-verification` → unsigned ZIP in build/distributions/; install via Settings → Plugins → ⚙ → Install Plugin from Disk… (full IDE restart required, no hot-swap for version changes).
<!-- SECTION:DESCRIPTION:END -->
