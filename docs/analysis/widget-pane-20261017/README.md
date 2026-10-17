# Widget pane — 17 October 2026

Implementation in `dvil/termux-ssh-analysis`; follows the approved widget-pane review. Earlier Settings pane edits remain intact. No commit, push, public release, or everyday launcher update is included.

## Behavior

- Reuses `HelpWindowPanel` for move/resize/maximize/reset and saved geometry. Portrait is windowed; landscape expands within the launcher host. The safe viewport receives the launcher's existing bar, fold, and keyboard margins.
- Normal viewing hides widget IDs and editing controls. Add, Edit/Done, and Commands are explicit actions; commands expand on demand and retain the existing add/show/resize/remove grammar. Empty panes have a central Add widget action.
- Picker loads current providers each time, keeps native previews and supports app/widget-name search. Stale asynchronous results cannot repopulate a dismissed picker; load errors offer Retry.
- Native `AppWidgetHostView` instances stay attached during editing/command toggles and survive layout changes by identity. Only Retry replaces a failed instance. Hidden panes stop listening.
- Saved grid positions survive rendering. Collisions move only affected widgets. Resizing anchors the opposite edge and uses the actual displayed grid when growing toward a boundary.
- Failed widgets offer Retry and explicit Remove. Tapping the failure-card background does not delete its binding.
- Launcher font refreshes stop at native widget hosts. Re:TUI controls use launcher typography; providers keep their own fonts and sizes.

## Persistence and recovery

Routine rendering no longer deletes the entire Android widget host or scans guessed widget IDs. Cleanup retains references from every other saved Space, including directories with damaged manifests. Missing/unreadable widget snapshots prevent pruning. Cloned Spaces retain their existing shared-binding semantics: removing one placement keeps the binding while another Space still references it.

The landscape run exposed an existing language-resource issue: contexts overrode the complete configuration and cached resource wrappers did not track configuration changes. Language contexts now override language only, and cached wrappers refresh when their source configuration changes. Orientation, window size, density and system font changes can reach pane controls without dropping installed translations.

## Validation

Final results are recorded in `validation.json` and the device result files beside this report. Device tests run in isolated `com.dvil.tui_renewed.termuxsshdebug` and test APKs, using a native RemoteViews provider in the test APK. They never bind or remove widgets from the everyday launcher package.

- Full unit suite: **463 total, 458 passed, five existing live-network skips, zero failures/errors**. Canonical Playstore debug APK and test APK builds passed without bypassing validation tasks.
- **24 device checks passed**: five widget checks in each orientation on each device (20), plus two language regressions on each device (four). Installed APK bytes on both devices matched `validation.json`.
- Vivo I2220, serial `10BE1C0FB40006A`, Android 16/API 36: portrait and actual landscape checks, plus language-wrapper regressions.
- Disposable API 35 emulator: the same portrait/landscape checks and language regressions.
- Widget checks cover the empty state, picker search, Back behavior, keyboard visibility, native taps/live updates, typography boundaries, attached-view retention, long-press drag, actual right-edge resize, Retry, hidden listening, maximize/recreation, empty/cloned Spaces, damaged manifests and unreadable snapshots.
- Language checks cover orientation/font/window-width propagation while retaining translation, plus import/switch/update/fallback/XML/removal behavior.

Initial fixture runs required notification/changelog preconditions and a standalone Java provider (the test APK does not include the target APK's Kotlin runtime). The first orientation attempt used system rotation settings without proving the activity configuration; final fixtures explicitly request and assert their orientation. These were corrected before collecting final results.

Screenshots use `phone-portrait-*`, `phone-landscape-*`, `emulator-portrait-*`, and `emulator-landscape-*` filenames. The native widget pictured is a test fixture, not a bundled user widget.

Broader third-party widget/configuration/collection compatibility, a full palette sweep, release lint/minification and public release packaging remain outside this focused pass.

The isolated test applications and binding grants were removed from both devices afterward. Phone rotation (locked portrait), emulator rotation settings, and input-method selections were retained/restored; the owned emulator was stopped. The normal wireless debugging connection was restored for continued work.
