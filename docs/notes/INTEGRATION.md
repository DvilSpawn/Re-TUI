# Integrated Notes

Re:Member's native editor now runs inside the Launcher package. The standalone
Re:Member checkout and its private data are unchanged.

- Each status-pane entry opens a stable note UUID and its complete Markdown file.
- Tapping the pane background/header or running `notes` opens the library.
- `notes -open <index or title prefix>` opens an individual file. Existing add,
  list, copy, lock, remove and clear commands use the same library. Copy returns
  the file body with protected passages hidden. A deletion lock is distinct from
  encrypted redaction and prevents remove/clear from deleting that file.
- Notes has its own `.notes` task affinity, `singleTask` activity, and Recents
  entry. It is launched with `NEW_TASK`, not for a Launcher activity result.
  Normal Launcher restart recreates Home without finishing Notes. Android process
  reclamation can still destroy activities; the selected file and editor position
  are restored from saved state, with content saved on edits and on pause.
- Appearance comes through the existing Launcher theme bridge and Settings frame.
  Android 6 support remains; font import uses the compatible native font loader.

## Storage and migration

On first access, legacy Launcher `notes.xml` entries become individual private
Markdown files under `files/notes-library/<folder UUID>/<note UUID>.md`. The index
is `files/remember-notes.xml`. Text, creation times, and deletion locks survive the
migration; duplicate legacy notes receive distinct deterministic UUIDs. The old
XML is retained unchanged as a recovery copy and is not the active store afterward.
A malformed legacy file/index fails closed instead of becoming an empty library.

The editor and status reader share a file IO lock. The editor refreshes its library
when returning from Home after another command changed the store. The note list
updates when the store revision changes, without reopening every Markdown file on
every status tick.

Notes remain private to Launcher. Existing Launcher configuration backups do not
include this private library; use the Notes export action for portable Markdown
and attachments. Exporting protected passages requires authentication and produces
readable content. No automatic transfer from the separate Re:Member app is added:
its protected passages use that app's Android Keystore keys.

## Verification

The integration tests exercise legacy migration, duplicate IDs, file round trips,
locked deletion, corrupt-index preservation, separate Home/Notes tasks, Launcher
restart, activity recreation, updates made while Notes is paused, and distinct
file-link/background taps. Model, Markdown, redaction-format, and export unit
checks were brought over from Re:Member.

The imported Re:Member source is covered by REMEMBER-LICENSE in this directory.

### 2026-09-13 validation

- Play Store debug build and all 171 unit tests passed.
- The three emulator integration tests passed on API 36.1; the editor screenshot
  was inspected for content and bounds.
- F-Droid Kotlin compilation passed before the final back-navigation and touch
  refinements; Android 6 runtime behavior has not been device-tested.
- Lint reports no errors in the new Notes code. Full-app lint remains blocked by
  existing widget-drawer localization format errors outside this integration.
- Installed Play Store debug Build 417 on phone serial `10BE1C0FB40006A` using
  an in-place update. Existing and new signing certificates matched, the original
  first-install date was retained, and Home was confirmed running afterward.
- The installed APK was pulled back and matched the local SHA-256:
  `3cc41b9a4c8bfb69ac7f47bcaf0bde04bf07e0dd2a999dee34371aa3caf920e2`.
- Manual/smart redaction and authentication are included. The physical phone's
  biometric/PIN reveal and export flows have not been exercised in this run.
- The final scroll guard passed its additional emulator test: dragging over a
  note does not open the file or library. Temporary emulator storage settings
  were restored and the instrumentation package was removed afterward.

### 2026-09-15 release validation (Build 418)

Play Store debug/release APKs, the signed Play Store bundle, F-Droid debug,
173 unit tests and full-app lint completed successfully (zero lint errors).
Eleven instrumentation tests passed on the I2220 over wireless ADB, covering
Notes, language packs and reminder removal. This supersedes the earlier lint
blocker above. An interrupted-write recovery regression was found and fixed:
index and Markdown backup files are now read through AtomicFile recovery.
The test simulates missing primary files with intact backups in isolated storage.
Biometric/PIN flows still require manual acceptance.

### 2026-09-16 release validation (Build 419)

The existing Notes and language-pack phone regressions passed alongside the new
Back-navigation and service reliability tests. Build 419 does not change Notes
storage or migration behavior.
