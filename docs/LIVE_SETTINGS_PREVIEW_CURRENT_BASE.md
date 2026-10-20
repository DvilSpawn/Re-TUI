# Live settings preview on the current codebase

20 October 2026. Branch: `codex/live-settings-preview-current`.
Base: local `master`, `6aafbc70` (Hotfix 4 / Build 507).

## Correction to the earlier report

The earlier implementation and its 12 emulator / 43 unit test results used
`dvil/settings-preview-carousel` at `6627e8cd`, which was 19 commits behind local
`master`. Those results do not validate the current codebase. The calculator
pack split was already committed on master; local calculator copies in that old
checkout were incorrectly described as outstanding calculator work.

This checkout starts clean from current master. It contains only the preview
changes, its tests and this report. No calculator source was copied from the old
checkout. The original checkout and its unrelated local changes remain intact.

## Current implementation

The current base already has `HomeAppearance`, `LauncherHomeSnapshot`,
`StatusPresentation`, `ModulePresentationSnapshot` and shared Home styling.
This implementation extends those existing components instead of importing the
older branch's parallel renderer architecture.

- The swipeable cards use actual captured status, terminal/input, suggestions,
  toolbar icons and native module content from the running launcher.
- Battery formatting is shared by `BatteryManager` and the preview. Saved and
  draft bars use the real Android battery reading, symbol, length and colors.
- Native module capture preserves rich text/Lua layouts, progress, buttons,
  notifications and music content without running scripts or retaining actions.
- Current Help and Calculator specimen pages remain, with an ANSI palette page
  added. These three pages are explicitly labeled as samples.
- Draft values overlay immutable saved appearance values. They do not write XML,
  replace launcher settings or change the global theme preview cache. Save is
  the persistence boundary.
- The selected page and drafts survive recreation. Visible cards refresh about
  every two seconds; refresh stops when detached or hidden.

The Integration entry and its page header are also renamed to **RE:TUI Keys Shortcuts**; the localization catalogue and template are synchronized.

## Verification

The current-base APK builds and installs as the isolated Play Store debug package
`com.dvil.tui_renewed.fetchdebug` on Android 16 emulator `emulator-5584`.

Focused unit validation: **49 tests passed**, including status presentation,
status order, theme resolution, shadows, font scaling, borders, suggestions,
unified status helpers and the palette registry checks. The two palette failures
reported against the old checkout do not reproduce in this selection on master.

Device validation and evidence are recorded in
[`build/settings-preview-current/`](../build/settings-preview-current/).
The focused checks cover saved/draft battery bars against actual Home content,
font/alignment parity, unified bars, transient settings, live terminal/input,
rich Lua content and inert buttons, ANSI draft/Save, physical swipe events,
recreation, current Help/Calculator cards, passive input and existing snapshot
presentation parity. Final result: **8 instrumentation tests passed**, followed by **1 additional all-status comparison test passing**. The latter compares every available status source, including the custom ASCII frame, and checks draft RAM color/alignment without saved writes.

The filtered APK build excludes `validateRuntimeNotices`; the ordinary unit
build runs that check. This is focused validation, not a full-suite claim.

## Evidence

- [All status rows: top](../build/settings-preview-current/all-status-live.png), [draft styling](../build/settings-preview-current/all-status-draft.png), [lower rows](../build/settings-preview-current/all-status-lower.png)
- [All-status test result](../build/settings-preview-current/all-status-test.txt)

- [Saved battery](../build/settings-preview-current/saved-battery.png), [draft battery](../build/settings-preview-current/draft-battery.png), [unified bar](../build/settings-preview-current/unified-battery.png)
- [Current Lua module with draft color](../build/settings-preview-current/lua.png)
- [ANSI specimen](../build/settings-preview-current/ansi.png)
- [Device results](../build/settings-preview-current/device-tests.txt), [unit results](../build/settings-preview-current/unit-tests.txt), [build log](../build/settings-preview-current/build.txt)
- [Current-base debug APK](../build/settings-preview-current/settings-preview-debug.apk)

## Limits

Only the connected emulator was used; no physical phone was connected. Statuses
other than the battery and current module/terminal content use the launcher's
latest captured result. Appearance changes do not start another status service,
weather request, Lua engine or media controller. Help, Calculator and ANSI cards
are samples. Preview dimensions differ from Home, and landscape, accessibility
and performance have not been revalidated in this run.

No push or release was performed. Local master integration was requested after validation.
