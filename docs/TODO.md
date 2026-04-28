# Re-TUI TODO

This is a working backlog for issues and user requests after the timer/pomodoro/widget-page branch was merged into `master`.

## Completed In v325

- Brightness permission flow
  - Added `WRITE_SETTINGS` to the manifest.
  - Updated the brightness command to deep-link into Re-TUI's own "Modify system settings" permission screen.

- Package name / side-by-side install
  - Confirmed active app id is `com.dvil.tui_renewed`.
  - Namespaced the custom `RECEIVE_CMD` permission to `${applicationId}` to avoid collision with the original upstream launcher.

- Themer navigation stacking
  - The settings/Themer terminal surface now forces an opaque internal background.
  - The outside overlay can still let wallpaper bleed through without showing prior Themer screens under the current one.

- Widget / terminal polish
  - Added separate theme keys for music and notification widget border/text colors.
  - Added auto-color support for those new keys.
  - Widget border labels now paint an opaque surface mask so border lines do not show through transparent themes.
  - Music song line now uses `Title:` instead of repeating `Now Playing:`.
  - Toolbar app drawer icon background was aligned with the other toolbar buttons.

- Branch/release
  - Merged `codex/timer-stopwatch` into `master`.
  - Published Firebase build `325`.
  - Created `codex/termux-integration` from the merged `master`.

## High Priority

## Widget / Terminal Polish

## Integrations

- Termux integration
  - Define the intended scope first: launch Termux, run a command/intent, expose Re-TUI commands to Termux, or sync config files.
  - Check current Android restrictions and Termux intent/API support before implementation.
