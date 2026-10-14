# Preserving editable module defaults

## Editable defaults

Bundled samples and the system timer now record their installed checksum independently
of the editable source. Repeated default installation updates only untouched copies.
Edited source, settings and data are preserved. Untracked existing copies are retained
when their origin cannot be established. Launcher-owned timer metadata supplies the
baseline for migration from older versions.

Deleting or renaming a tracked default records its deliberate removal. Future default
installation leaves it removed. A missing previously installed default is also treated
as a deliberate deletion. To recreate it, use the module editor explicitly.

Script/manifest writes and the default registry use Android AtomicFile. A pending
checksum is persisted before default source changes. The next update completes a
partially applied source/manifest update, retries a write interrupted before source
replacement, or preserves a user edit made since the interruption. Preferences are
never replaced by the default updater. Source changes still invalidate permission
approval under the existing rules.

## Phone verification

The completion suite checks untouched updates, edited/deleted defaults, and interruptions
before and after source replacement. Settings and data stay intact. Library, preview
and compatibility tests run separately against the existing debug app on the phone.
