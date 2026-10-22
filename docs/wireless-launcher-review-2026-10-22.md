# Wireless launcher review — 22 October 2026

Device: Vivo I2220, wireless debugging. Existing Play Store debug signing identity;
installed in place without clearing data. Source work remained in the Terminal
mode worktree; main checkout and master were not modified.

## Notifications

The startup log reported `IllegalArgumentException: Conflicting setting: filter`.
The active app-owned notifications.xml contained two valid filter records with
different IDs. Singleton-option duplicate validation incorrectly rejected these
repeatable records. Notification filter and format records are now excluded from
singleton deduplication; conflicting singleton options still fail validation.
The device's original notifications.xml is unchanged byte for byte, and both
filter records survive. No deletion, reset, or filtering-rule rewrite was needed.

## Classic frames

Cyberdeck was enabled, dashed borders disabled, and individual status borders
enabled. Shared surface styling incorrectly gated borders on dashed mode alone.
Saved and draft surface styling now accept Cyberdeck independently, while still
respecting each surface's border toggle. The real Classic screen displays status
frames after installation. This was a rendering gate, not a preset problem.
Screenshot: /tmp/classic-status-frames-fixed.png.

## Issue 27 time-format probe

A read-only phone probe attached a passive settings preview using a temporary
`'ISSUE27_DRAFT' HH:mm` draft, without opening or saving the settings editor.
The saved Home clock was available and the device's custom clock-word format
rendered. The draft marker was absent from the preview, reproducing its stale
saved-clock formatting. All saved XML, frame and wallpaper state remained
unchanged by this probe. No time-format preview correction was implemented in
this review; no GitHub reply was posted.

## Checks

- Standard app and instrumentation builds passed.
- 11 focused XML migration and border tests passed.
- Opt-in wireless device review passed: filter validity, Classic frames, saved
  clock availability, transient preview probe and configuration non-mutation.
- The device report recorded `draftTimeFormatRendered=false`, a confirmed open
  defect rather than a passing preview-format result.
- Fresh launcher process logs no longer contain the filter conflict. Existing
  unrelated locked-profile and missing-application warnings were not addressed.

## Saved time-format reload correction and clean install

A later phone inspection found status_time_format stored as a clock-words format
while Home still displayed the prior numeric clock. refreshUiInPlace reloaded
XML and recreated UIManager but retained TimeManager's startup-only formatter
cache. It now recreates that formatter after settings reload. Literal backslash-n
is also accepted alongside the existing %n newline notation; otherwise the saved
backslash-n pattern raised a date-pattern error when finally reloaded.

A new opt-in TimeFormatReloadIntegrationTest passed on the wireless phone. It
changes both formats, refreshes in place, compares the actual Home status text
with the reloaded formatter, checks separate output formatting, and exercises
both newline notations. Original settings are restored in finally.

After that passing check, the app was uninstalled and freshly installed as the
user requested. Private data and app-owned external files were backed up under
~/Downloads/ReTUI-backups/pre-clean-install-20261022 before removal. The fresh
app launched successfully into setup step 1; the user's setup choices were left
for them. Termux was not uninstalled. A signed test APK is available at
~/Downloads/ReTUI-builds/ReTUI-terminal-mode-time-format-fix.apk.
The separate draft-preview clock-format issue remains outside this correction.

## Draft status clock correction

The separate status-row preview defect is now corrected. StatusPresentation
renders draft time rows through a private instance of the existing TimeManager
formatter instead of reusing captured Home clock text. Draft formats, separators,
clock words, explicit sizes, inline colors and theme colors are respected.
Invalid in-progress formats stay in the preview without sending errors to Home
or replacing the live formatter. Unified compact status retains its existing
platform date/time display, matching the corresponding Home surface.

App and instrumentation builds passed. StatusClockDraftTest passed on the
wireless phone, covering refreshed text, multiline formatting, separate explicit
and base sizes, draft and inline colors, multiple formats, incomplete patterns
and separators, and unchanged saved values/live formatter identity. The updated
app was installed in place; the downloadable APK was refreshed. This was a
focused renderer check rather than a rerun of the full settings UI suite.
