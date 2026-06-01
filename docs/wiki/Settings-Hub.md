# Settings Hub

Re:T-UI now includes a terminal-style settings hub.

Open it with:

- `settings`

Older builds may still accept `themer` as a compatibility alias, but `settings` is the current public command.

## Sections

The settings hub is grouped into:

- Appearance
- Behavior
- Personalization
- Integrations
- System and Support

## What It Does

The hub is not meant to replace the command line. It is meant to:

- make discovery easier
- reduce XML hunting
- keep the terminal visual identity intact

## Appearance

This section includes:

- `theme.xml`
- `ui.xml`
- `toolbar.xml`
- `suggestions.xml`
- Fonts
- Presets
- Wallpaper picker
- Live wallpaper picker

## Behavior

This section includes:

- `behavior.xml`
- `apps.xml`
- `notifications.xml`
- `cmd.xml`

## Personalization

This section includes:

- `alias.txt`
- Toolbar Buttons
- `ascii.txt`
- `rss.xml`

Toolbar Buttons exposes two optional command buttons for the launcher toolbar. Each slot is empty and disabled by default. A slot can run the same text a user would type at the prompt, such as an app name, alias, or full command.

## Integrations

This currently includes:

- preferred music app selection
- music module behavior through `behavior.xml`

## Terminal UI Rules

The settings hub intentionally follows the same visual language as the launcher:

- square terminal borders
- attached label tabs
- wallpaper visible outside the settings window
- terminal-styled dialogs for confirmations and pickers

## Save Behavior

- XML changes are staged in the editor until you press `SAVE`
- Back or `CANCEL` without saving triggers a discard warning
- Saved settings route through the launcher settings layer so they survive reloads more reliably

## Why This Matters

The original project expected users to live in config files.

Re:T-UI still supports that, but the settings hub makes the launcher much easier to adopt without diluting its character.
