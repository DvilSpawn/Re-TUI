# Re:TUI 2 — Build 412

Build 412 is a reliability update for presets, wallpaper auto-theming, and live Launcher customization.

## Fixes

- Fixed preset save and apply failures, including older presets that use legacy color-setting names.
- Hardened preset XML parsing while continuing to reject unsafe document declarations.
- Saving a preset no longer applies it or replaces the current appearance.
- Wallpaper auto-theming now remains active while individual theme and suggestion colors are overridden manually.
- Running `wallpaper -auto` starts a fresh derived palette and clears earlier manual overrides.
- Settings color fields automatically use readable black or white text against their current background.
- Live Launcher refreshes are posted safely after settings changes, avoiding Activity-state crashes.
- Launcher refreshes preserve the loaded app preferences used by suggestion rows.
- Added breathing room above the unified bottom status and dock area.

## Validation

- Play Store unit tests and lint passed.
- Signed GitHub APK and Play Store AAB were built from the same tagged source.

Version 2, Play Store version code 412.
