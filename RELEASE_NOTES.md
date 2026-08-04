# Re:T-UI — Next Major Update

This is a major update focused on making Re:T-UI more reliable, easier to configure, and more useful without taking away its terminal-first character.

## Highlights

### A complete reminder workflow

- Added a dedicated reminder pane for viewing, adding, editing, and removing reminders.
- Simplified the reminder module into a quick preview with an `open` action for the full pane.
- Added terminal commands for the complete reminder lifecycle:
  - `reminder -add <task> <dd/mm/yy> <HH:mm>`
  - `reminder -ls`
  - `reminder -rm <ID>`
- Reminder text now follows the configured launcher typeface and reminder-pane accent colour.

### Native Weather rebuilt

- Replaced the old native Weather implementation with MET Norway Locationforecast.
- Native Weather no longer requests Android location permission.
- Weather uses the location configured by the user as a place name or latitude/longitude pair.
- Added local ASCII weather artwork, unit conversion, caching, attribution, and clearer error messages.
- The native Weather module and status-pane Weather row are now independent. Hiding one no longer disables the other.
- The native Weather module now displays the configured location.
- Termux and `wttr.in` Weather modules remain separate and unchanged.
- Fixed a refresh loop that could create thousands of requests and eventually crash the launcher.

### Better preset sharing and importing

- Shared presets now include approved `ui.xml` appearance settings alongside theme and suggestion settings.
- Personal information, device-specific values, fonts, and behavioural settings remain local.
- Importing a shared preset now adds it to the preset library without immediately overwriting the active configuration.
- Imported name conflicts create a numbered copy instead of replacing an existing preset.
- Added preset removal through Settings and `preset -rm <name>`.
- Existing older presets containing only theme and suggestion files remain supported.

### Tasker V2: switch Spaces

- Tasker actions can now select and switch to a Re:T-UI Space.
- Supports normal Space selection and Tasker-provided variables.
- Added validation for missing and unknown Spaces.
- Reduced repeated launcher initialization and font loading during Space switches.

### Optional launcher sound pack

- Added a bundled Re:T-UI sound pack with an opt-in master switch.
- Added individual controls for boot, click, success, failure, notification, reminder, and timer sounds.
- Individual events can be silenced without disabling the entire sound pack.
- Existing Android reminder and timer tones remain the fallback when the sound pack is disabled.

## Commands and power-user tools

### New `inspect` command

- Added `inspect <app or package>` for examining an installed application.
- Reports app identity, common intent handlers, exported activities, receivers, services, providers, and required component permissions.
- Installed applications are offered as suggestions when `inspect` is entered.

### `intent` is now dispatch-only

- Removed `intent -check` as a clean breaking change.
- `intent` now focuses only on constructing and dispatching Android intents.
- Added readable suggestions for Open URI, Start activity, Send broadcast, and Parse intent URI.
- Intent parameters now use readable labels such as `Action (-a)` and `Component (-n)`.

### Other command improvements

- `alias -file` now opens the built-in Re:T-UI text editor instead of an external file picker.
- Removed the obsolete `theme` command and legacy online theme manager.
- Current presets, appearance settings, wallpaper colour extraction, and Tasker theme controls remain available.

## Settings and interface improvements

- Toolbar shortcut icon settings now provide a visual picker with each supported icon and its name instead of an undocumented text field.
- Added 12 toolbar shortcut choices: Star, Bell, Chat, Music, Timer, Note, Search, Refresh, Home, Terminal, Lock, and Apps.
- Moved sound controls directly to **Behaviour > Sounds**.
- Fixed the oversized arrow-mode output header so it matches the height of the normal header.
- Added support for animated ASCII imports and related viewport, timing, alignment, and colour controls.
- Improved the launcher README and removed obsolete theme workflow documentation.

## Reliability and data safety

- Backup protection is now selected before Android creates the destination file.
- Backup data is generated and validated before the destination is opened.
- Completed exports are read back and verified byte-for-byte before success is reported.
- Failed exports remove the newly created destination when possible, preventing misleading zero-byte backup files.
- Encrypted backups continue to use authenticated AES-GCM protection.
- Corrected timer and Pomodoro progress bars so they count down instead of filling in the wrong direction.
- Improved launcher initialization during Tasker Space switches.
- Fixed native Weather request duplication, module routing, and crash behaviour.

## Breaking changes

- `intent -check` has been removed. Use `inspect <app or package>`.
- The obsolete `theme` command has been removed. Use the preset commands or **Settings > Appearance > Presets**.

## Play Store summary

Re:T-UI's next major update adds a complete reminder pane and CLI, permission-free native Weather, richer preset sharing, Tasker Space switching, optional per-event launcher sounds, safer verified backups, animated ASCII controls, visual toolbar icon selection, and the new `inspect` command. It also fixes Weather crashes, zero-byte backup files, preset import behaviour, timer progress, and oversized arrow headers.
