# Re:TUI 2 — Build 411

Build 411 adds a focused Search launcher, a simpler first-run setup, configurable frame packs, and a substantial runtime cleanup.

## Search mode

- Added an optional search-only home screen with categorized, horizontally scrollable results for apps, contacts, notifications, commands, aliases, and app groups.
- Added web-provider results and contextual command parameters without searching Launcher settings.
- Added `mode search` and `mode classic`, with only the destination mode offered as a suggestion.
- Kept calculator, podcast, Termux, and tmux panes usable from Search mode.
- Refreshed suggestions when returning to Launcher so stale results do not survive an app launch.

## Startup and layout

- Replaced first-run questioning with one staged Basic or Advanced setup pane.
- Added optional wallpaper auto-theming, solid/dashed/no-border choices, and independent Cyberdeck and CRT effects.
- Launcher settings now refresh the running UI in place instead of recreating the Activity.
- Rebuilt unified status as a compact bottom console with on-demand modules and tighter storage, network, RAM, unlock, and weather rows.
- Added output-pane auto-hide with content-sized expansion and keyboard-safe terminal sizing.

## Appearance and frames

- Added named frame packs, per-element frame controls, and a master Frames on/off switch that preserves the active pack.
- Added direct `.retui_ui.zip` package import with manifest and PNG validation.
- Bundled the inactive-by-default **Sprout Lands — Art by Cup Nooble** frame pack with attribution and license notice.
- Added a staged typography editor covering Launcher text surfaces.
- System wallpaper is now the first-run default; wallpaper auto-theming keeps its overlay transparent.

## Reliability and privacy

- Shared presets now use an explicit allowlist and exclude personal or device-specific values.
- Presets preserve built-in frame references without exporting bundled artwork.
- Released inactive bitmap and wallpaper resources, bounded caches, and removed obsolete compatibility paths and dead code.
- Fixed live-wallpaper scene refresh and improved frame-pack save/update behavior.

## Validation

- Play Store unit tests and lint passed.
- Signed GitHub APK and Play Store AAB were built from the same tagged source.
- Calculator and Termux pane launching were verified on a physical phone while Search mode remained active.

Version 2, Play Store version code 411.
