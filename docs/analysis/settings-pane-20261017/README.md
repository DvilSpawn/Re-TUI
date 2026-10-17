# Settings terminal pane — 17 October 2026

Settings Hub (`ThemerActivity`) and settings/config editors (`TuixtActivity`) now reuse the same `HelpWindowPanel` as Calculator and Help. The shared `settings_surface.xml` provides the raised title tab, framed body and transparent surroundings. Existing settings colors, custom frame targets and display-only color safety remain in use.

- Portrait opens windowed with move/resize, maximize and reset controls. Geometry is saved across pauses and activity recreation. Hub and editors retain separate window geometry.
- Landscape expands the pane and hides size controls, matching Calculator/Help behavior.
- Both surfaces use explicit system-bar, display-cutout and keyboard insets. Bottom controls reserve room for resize handles.
- Existing navigation, frame/typography drafts, previews and Save/Cancel behavior are retained. No imported preset values are rewritten by pane styling.

## Validation

Android 15 / API 35 disposable emulator, isolated `com.dvil.tui_renewed.termuxsshdebug` application:

- 12 targeted device checks passed: pane geometry/header/touch targets, maximize/resize/recreation, navigation, frame/typography draft retention, editor draft/Save/Cancel boundaries, preview rendering and portrait keyboard visibility. See `device-results.txt`.
- Final four-case pane/recreation rerun passed (`pane-portrait-results.txt`).
- Two landscape editor/navigation checks passed (`pane-landscape-results.txt`); captured landscape dimensions are 1067 × 480. The initial landscape fixture assumed a visible maximize control; it was corrected to require the intentional hidden state in expanded landscape.
- Canonical Playstore debug app and Android test APK builds passed. Full unit suite: 457 total, 452 passed, five existing opt-in live-network skips, zero failures/errors. No validation tasks were bypassed.
- The optional fetchDebug/paletteDebug build attempt encountered the existing missing release-runtime-classpath problem in validateRuntimeNotices. Device checks used the canonical isolated Termux debug variant instead.
- `git diff --check` passed.

Screenshots: `settings-pane-home.png`, `settings-pane-hub.png`, `settings-pane-editor.png`, `settings-pane-editor-landscape.png`.

The phone was not connected; no phone installation or new phone test was performed. No release lint/minified release packaging, commit, push or publication was performed for this change. Emulator rotation/IME preferences restored; disposable test applications removed and the owned emulator stopped without wiping the AVD.
