---
id: KILO-2
title: 'JetBrains plugin: lux builds install side-by-side (separate plugin id/name)'
status: Testing
assignee: []
created_date: '2026-10-02 19:31'
updated_date: '2026-10-02 19:31'
labels: []
dependencies: []
type: enhancement
ordinal: 2000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Lux builds (versions containing `-lux<N>`, accepted since KILO-1) currently still build under the production plugin identity `ai.kilocode.jetbrains` / "Kilo Code", so installing one replaces the installed stable plugin.

Make the identity automatic: in `packages/kilo-jetbrains/build.gradle.kts`, when the resolved version carries the `-lux<N>` suffix, patch the plugin configuration to:
- id `ai.kilocode.jetbrains.lux`
- name "Kilo Code (lux)"

Everything else (settings storages, settings-page IDs, tool windows, actions) stays identical. Because settings storage files are filename-keyed in the IDE `options/` folder, lux and stable share settings. Content module names need no change (unique-within-plugin is the only requirement).

Constraint: install both side-by-side but keep exactly one enabled at a time — two enabled instances would collide on settings-page IDs, tool windows, and actions, and both services would write the same settings files. The distinct plugin ID also keeps lux builds invisible to marketplace update logic.

Test ZIP: `script/build-version.sh 7.1.8-lux1 --skip-signing --skip-verification` → install from disk alongside stable Kilo Code.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Lux builds (version carrying `-lux<N>`) now build under a separate plugin identity so they install side-by-side with the published plugin:

- `packages/kilo-jetbrains/build.gradle.kts`: added `val lux = Regex(".*-lux[0-9]+").matches(ver)` right after version resolution; `pluginConfiguration.id` is `ai.kilocode.jetbrains.lux` and `pluginConfiguration.name` is `Kilo Code (lux)` when lux, otherwise unchanged production identity. The IntelliJ Platform Gradle Plugin patches these into plugin.xml at build time.
- Settings storages are filename-keyed (`kiloAutocompleteSettings.xml`, `kiloLogSettings.xml`), so lux and stable share settings in the same IDE config dir.
- Content module names untouched: the modular-plugins spec only requires uniqueness within a plugin.
- Usage constraint documented in the ticket: keep exactly one of the two plugins enabled at a time (settings-page/tool-window/action IDs would collide if both run).

Test build command: `script/build-version.sh 7.1.8-lux1 --skip-signing --skip-verification` (awaiting user go-ahead).
<!-- SECTION:FINAL_SUMMARY:END -->
