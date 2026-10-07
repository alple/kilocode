---
id: KILO-8.4
title: 'JetBrains: Deluxe parameter component — picker UI, routing option, staleness'
status: Done
assignee: []
created_date: '2026-10-06 13:05'
updated_date: '2026-10-07 12:50'
labels: []
dependencies:
  - KILO-8.2
  - KILO-8.3
parent_task_id: KILO-8
priority: high
type: enhancement
ordinal: 24000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Decisions implemented

[Decide the picker shape](KILO-7.3) + [ADR-0001](docs/adr/0001-deluxe-owned-route-picker.md) (Deluxe-owned component, fork-marked package, per-turn route transport); [Settle what "choosing a provider" means](KILO-7.2) (in-picker route choice, freeze semantics); [Route-support scope](KILO-7.6) (OpenRouter-only v1, enable-model-routing option); staleness from [new-session defaults](KILO-7.4) #4 + ADR-0002 (keep-pin).

## Scope

**1. Fork-marked package — first convention in this plugin:** `ai.kilocode.client.deluxe` (decided 2026-10-06; no marker convention needed in kilo-jetbrains paths). All new files live there.

**2. Deluxe parameter component** (session header):
- Rows for **model + route + reasoning effort**, plus a **lock chip**: model+route+effort editable until the session's **first message**, then they lock **as one**; unfreezing is an explicit user action; any change applies **from the next message onward**; frozen selection sticky per session. New sessions start unlocked with inherited (store) values as ordinary editable values.
- List UI **reuses generic `PickerPopup`** (`client/ui/picker/PickerPopup.kt`, 2 usages, stable) as the shell with **fork-owned renderer/rows** (route data: tag, provider name, quantization, status, uptime, pricing; Auto row = no pin). `ReasoningPicker` is JBPopup-based — not the shell precedent.
- **Upstream edit limited to the session-header binding swap**: `PromptPanel.kt:148-153` / `SessionUi.kt:705-724` (~12-line scale, rewind precedent). Models settings, New Worktree dialog, AgentsConfigurable keep the upstream picker unchanged.

**3. Route-list bridge**: JetBrains backend fetch of the CLI route-list endpoint (new backend manager/`KiloBackendWorkspace`-style fetch + `KiloCliDataParser` mapping), new shared DTO + RPC method (workspace-scoped), consumed by the frontend.

**4. "Enable model routing" option** (Deluxe-only, gates ALL route UI — off/absent ⇒ no route UI anywhere): configurable route-capable provider-id list, **defaults `openrouter` + `openrouter-private`**, user-extendable for custom-URL OpenRouter providers; client-side id matching (no new provider-state DTO fields). Option storage in fork-owned code.

**5. Staleness handling**: a pinned route missing from the refreshed list is **NOT auto-cleared** — the pin stays active until the user explicitly re-picks (unfreeze required when locked); notice + refreshed route list (re-fetch on demand) make the recovery path visible. Accepted consequence: a dead pin (`allow_fallbacks: false`) fails requests hard until re-picked.

## Hard constraints

- **No `ModelStateDto`/`model.json` change**; selection unit stays `(providerID, modelID)`.
- Selection/variant/route writes go through the project parameter store (store ticket); this ticket consumes it.
- Upstream `ModelPicker`/`PickerPopup` internals untouched for routes.

## Verification

- Component/controller tests (route rows gated by the option; lock engages at first message; unfreeze flow; staleness keep-pin + notice); existing picker tests stay green; header binding swap covered by session UI tests.
<!-- SECTION:DESCRIPTION:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-10-07 10:31
---
**Parked mid-implementation (context budget)** — working tree holds an uncommitted, NOT-yet-compiling implementation. Next session resumes from here.

**Done, uncommitted:** Bridge (compiles): shared `RouteEndpointDto.kt` (RouteEndpointDto/RouteUptimeDto/RoutePricingDto), `KiloWorkspaceRpcApi.routes(directory, author, slug)`, backend `KiloWorkspaceRpcApiImpl.routes` (OkHttp `GET /kilo/routes/{author}/{slug}?directory=…`, 404⇒empty), `KiloCliDataParser.parseRoutes`, `KiloWorkspaceService.routes` (throws so the picker can show Failed), `FakeWorkspaceRpcApi.routes` + `routeLists`/`routeCalls` test hooks. Deluxe files in `frontend/src/main/kotlin/ai/kilocode/client/deluxe/`: `KiloRoutingOptions.kt` (app-level option: enabled default off, provider list default openrouter+openrouter-private, sanitize with fallback to defaults, routesEnabled(provider)), `KiloParameterRows.kt` (KiloParamRow Lock/Model/Route/Effort/Note + kiloParameterRows builder reusing modelPickerRows + routeSectionRows w/ stale-pin note (keep-pin) + routeAuthorSlug + KiloRouteText pricing/uptime/degraded), `KiloParameterPicker.kt` (PickerButton; PickerPopup Mode.Multi + search; callbacks onModel/onRoute/onVariant/onClearVariant/onUnlock/favorites/fetchRoutes; fetch on popup-open and model-pick with generation token; lock icon while frozen), `KiloParameterRenderer.kt` (model rows mirror upstream ModelPickerRenderer incl. favorite stars; route rows tag/provider/quantization/uptime/pricing/degraded; effort rows; Lock row with unlock action; weak text while frozen), `KiloRoutingConfigurable.kt` (settings page + ProviderList add/edit/remove). Shared edits: `SessionModel.parametersFrozen` (loadHistory⇒history.isNotEmpty, clear⇒false), `SessionController` (freeze in dispatch() firing WorkspaceReady; unfreezeParameters(); clearVariant() + store.clearVariant; frozen guards in selectModel/selectVariant/selectRoute/clearModelOverride; routeFor internal; selectVariant/selectRoute fire WorkspaceReady so the SessionUi branch re-syncs the picker synchronously — NO new SessionModelEvent), `PromptPanel` (`parameters: PickerButton?` ctor param; bar swaps model+reasoning for it when set; idleFill/onPickClose applied), `SessionUi` (parameters field + wiring + WorkspaceReady `parameters.sync(...)` + `setResetVisible(m.modelOverride && !m.parametersFrozen)`), `KiloWorkspaceService.routes`, store `clearVariant`, bundle keys `param.picker.*` + `settings.routing.*`, XML registration `ai.kilocode.jetbrains.settings.routing` + root-page ActionLink.
---

created: 2026-10-07 10:31
---
Design notes: popup stays open (Mode.Multi) so model→route→effort can be set in one pass; search filters model rows only; route section rendered only when `routesApply(routesEnabled, item)`; freeze triggers = dispatch() of prompt/command + non-empty history at load; unlock = explicit popup Lock row → controller.unfreezeParameters(); next dispatch re-freezes; picks refused while frozen both picker-side and controller-side. Staleness: fetch per popup-open/pick with generation token; Ready + pin missing ⇒ Note row (param.picker.stale) + LOG.info; pin stays in store/pref until explicit re-pick.

**KNOWN BROKEN — does not compile (fix in order):** (1) `KiloParameterRenderer.kt` — import must be `ai.kilocode.client.session.ui.style.SessionUiStyle`; verify `SimpleTextAttributes` import present; (2) `KiloParameterPicker.kt` — add `import javax.swing.JList`; public `fetchRoutes` exposes internal `KiloRouteState` ⇒ make the class `internal`; `showPopup()` calls removed `currentRows()` — use `rowsFor(\"\")` and keep the PickerPopup ref local for `refresh` while the JBPopup gets the closed-listener; `LOG.info { }` must become `LOG.info("…")` (KiloLog.info takes a String, not a lambda); (3) verify `AllIcons.Actions.Lock` exists on the platform (fallback `AllIcons.Nodes.Lock`); (4) `KiloRoutingConfigurable.kt` may still hide errors — recompile after the above; (5) then `./gradlew compileKotlin test` from `packages/kilo-jetbrains/`.

**Not started:** tests — controller lock/unfreeze/guards (ParameterStoreSelectionTest style: `model.parametersFrozen`, clearVariant, refusal paths, re-freeze after unlock), rows gating (`routeSectionRows` off⇒null, on⇒Auto+endpoints+stale note), `parseRoutes` parser test, `KiloRoutingOptions` sanitize/defaults, settings page isModified/apply; confirm existing picker/session tests stay green.
---

created: 2026-10-07 10:31
---
Continuation context: all work lives in the working tree (git status shows 12 modified + 6 new files, none committed). Files: shared/src/main/kotlin/ai/kilocode/rpc/dto/RouteEndpointDto.kt (new), shared/…/KiloWorkspaceRpcApi.kt, backend/…/cli/KiloCliDataParser.kt (parseRoutes + imports), backend/…/rpc/KiloWorkspaceRpcApiImpl.kt (routes impl), frontend/…/app/KiloWorkspaceService.kt (routes wrapper), frontend/…/deluxe/{KiloRoutingOptions,KiloParameterRows,KiloParameterRenderer,KiloParameterPicker,KiloRoutingConfigurable}.kt, frontend/…/session/model/SessionModel.kt, …/session/controller/SessionController.kt, …/session/SessionUi.kt, …/session/ui/prompt/PromptPanel.kt, frontend/…/settings/KiloSettingsConfigurable.kt, frontend/src/main/resources/kilo.jetbrains.frontend.xml + messages/KiloBundle.properties, frontend/src/test/…/testing/FakeWorkspaceRpcApi.kt. Full design + decisions live in the tickets KILO-7.2/7.3/7.6, ADR-0001/0002 and docs/adr/. Re-read this ticket's two comments before continuing; this comment ends the parking note.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Resumed from the parked working tree and finished the implementation. Compile fixes: correct SessionUiStyle import + missing SimpleTextAttributes/RouteEndpointDto imports and a KiloRouteText-based routeDetails helper in KiloParameterRenderer; javax.swing.JList import, internal visibility, rowsFor("") instead of the removed currentRows(), String-based KiloLog.info in KiloParameterPicker; Note row was missing its `: KiloParamRow` supertype; ModelPickerRow-typed renderModel; lock icon is AllIcons.Nodes.Locked (Actions.Lock and Nodes.Lock do not exist on IU-2026.1 — verified against the platform jar); settings page: javax.swing.ScrollPaneConstants import, createComponent returns JComponent (internal KiloRoutingPage no longer leaks), receiver-based calls instead of self-referencing the list under construction, DefaultListCellRenderer import; PromptPanel gained the PickerButton import for the parameters ctor param. Test updates for the new freeze semantics: WorkspaceReady added to exact event sequences (SessionCreation/ViewSwitching/ListenerLifecycle/HistoryLoading) and the post-failure selection tests now unlock before selecting (SessionRetry x6, ParameterStoreSelection) per ADR-0001; KiloSettingsConfigurableTest root-page order includes Model Routing. New tests: ParameterFreezeTest (10: unlocked start, freeze at first message with event, silent re-lock, frozen-at-load, refusal of selectModel/selectRoute/selectVariant/clearVariant/clearModelOverride while frozen incl. no store writes, unlock + re-freeze on next message, no-op unlock, WorkspaceReady from route/effort picks, clear resets), KiloParameterRowsTest (15: routeAuthorSlug, Auto+endpoints, stale-pin note keep-pin, loading/failed notes, routesApply gate, search narrows to models, lock row, stable row keys, pricing/uptime/quantization/degraded helpers), KiloRoutingOptionsTest (6: defaults off + OpenRouter pair, sanitize trim/dedup/blank-drop, empty→defaults fallback, routesEnabled gate + case-sensitive ids, loadState sanitize, service round-trip), KiloRoutingConfigurableTest (5: isModified fresh/behind-open-page, toggle→apply write-through, provider sanitize, gate off disables page), KiloParameterPickerTest (6: empty disabled, composed label, Auto/hidden route part, lock icon, id→key resolution, favorite keys), KiloCliDataParserTest Routes group (5: full row, camelCase aliases, tolerant nulls, skips non-objects, malformed→empty), PromptPanel binding swap test (parameters picker replaces model+reasoning in the bar). Result: `./gradlew test` = 4461 tests, 2 failures — TitleButtonTest New Session/New Worktree centering, confirmed failing on a clean HEAD (pre-existing, unrelated; verified via stash). `./gradlew typecheck` green. Remaining for review: manual sandbox pass of the popup (route fetch against a live CLI, freeze/unfreeze UX) is not covered by tests.
<!-- SECTION:FINAL_SUMMARY:END -->
