# Re:TUI 2 — Build 415

Build 415 makes local presets complete and lets users decide exactly which behavior settings to include in a shareable configuration.

## Changes

- Added a review screen before shareable export showing every available `behavior.xml` value as an individual toggle.
- Kept the existing safe behavior allowlist enabled by default; personal and executable values require explicit selection.
- Recorded and validated the exact selected behavior fields so unselected values cannot enter through a modified archive.
- Changed new local presets to copy the active `behavior.xml` exactly instead of applying the shareable sanitizer.
- Restored selected behavior fields when importing and applying a shared preset.

## Validation

- Play Store unit tests and lint passed.
- Signed GitHub APK and Play Store AAB were built from the same tagged source.

Version 2, Play Store version code 415.
