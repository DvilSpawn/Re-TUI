# Re:TUI 2 — Build 414

Build 414 makes wallpaper auto-theming non-destructive and improves local and shared preset handling.

## Changes

- Changed `wallpaper -auto` to save a one-time color snapshot instead of continuously overriding theme edits when the wallpaper changes.
- Added `preset -duplicate [name]` with a new-name prompt, preserving the complete local preset including its behavior and frame files.
- Preserved selected preset names inside shareable configurations so restored copies retain their original name and receive a numbered suffix only on collision.
- Kept executable commands, credentials, locations, device paths, and personal text excluded from shareable behavior data.

## Validation

- Play Store unit tests and lint passed.
- Signed GitHub APK and Play Store AAB were built from the same tagged source.

Version 2, Play Store version code 414.
