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
