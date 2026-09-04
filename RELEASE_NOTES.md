# Re:TUI 2 — Build 413

Build 413 adds Launcher-side Re Keyboard app shortcuts and repairs shareable configuration exports on Android XML providers.

## Changes

- Added a miniature QWERTY shortcut editor under Integrations with up to two app mappings per letter.
- Added live app-name and package filtering to the shortcut app picker.
- Added secure opaque shortcut IDs, private pairing tokens, work-profile-aware app resolution, and cache-backed icon sharing through the existing Keyboard context channel.
- Removed incompatible vendor-specific XML feature calls that prevented saved presets from being exported as shareable configurations on some phones.
- Kept preset and podcast XML protected by explicit document-type rejection and external-entity blocking.

## Validation

- Play Store unit tests and lint passed.
- Signed GitHub APK and Play Store AAB were built from the same tagged source.

Version 2, Play Store version code 413.
