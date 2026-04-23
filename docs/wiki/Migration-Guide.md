# Migration Guide

This page is for users coming from the original T-UI launcher or from older Re:T-UI builds.

The short version:

- the command-first identity is still intact
- Re:T-UI is more stable on modern Android
- a lot of quality-of-life work has moved into the settings hub
- presets are now the preferred theme workflow

## What Stayed the Same

These ideas are still core to the launcher:

- commands are the primary interface
- config files are still real and visible
- theming is still central
- the launcher still feels like a terminal, not a skin

If you liked the original project because it respected power users, that part is still here.

## What Changed

### Settings now have a terminal-style UI

Older T-UI expected users to hunt through files for almost everything.

Re:T-UI still allows that, but now includes a settings hub:

- `themer`
- `settings`

This makes discovery easier without taking away the underlying files.

### Presets replace the old theme-sharing flow

The upstream `theme` command still exists for compatibility, but Re:T-UI now treats `preset` as the real theme snapshot system.

Use:

- `preset -save <name>`
- `preset -apply <name>`
- `preset -ls`

### Wallpaper auto-color is now first-class

You can let the launcher derive colors from the current wallpaper with:

`wallpaper -auto`

This is one of the biggest shifts from the older manual theme workflow.

### App drawer is now a real visual surface

Instead of just printing app lists, Re:T-UI includes a terminal-style drawer with:

- alphabet tabs
- group tabs
- hidden app support
- group-aware app organization

### Notification handling is more split by surface

Re:T-UI now separates:

- notification terminal widget
- notification output in the command history

That means you can keep notifications visible on screen without necessarily dumping them into the output terminal.

### Music UI moved toward a visualizer

Rather than trying to be a full Android widget clone, the current music surface is treated more like a themed terminal visualizer.

## Best Migration Path

If you are coming from old T-UI, this is the cleanest adjustment path:

1. Open `themer`
2. Confirm font and wallpaper settings
3. Run `wallpaper -auto`
4. Tweak colors if needed
5. Save the result with `preset -save <name>`
6. Use `apps -hide` and app groups to rebuild drawer structure
7. Configure notifications and music preferences from the settings hub

## What To Stop Relying On

If you are bringing old habits forward, these are the things to de-emphasize:

- treating `theme` as the main theme system
- assuming every visual change should be made by raw XML first
- assuming no UI surface exists for config discovery

Those paths still exist, but they are no longer the best starting point.

## Compatibility Mindset

Re:T-UI tries to keep old command muscle memory useful, but it does not aim to freeze the project in the original architecture forever.

The practical rule is:

- compatibility where it helps users
- modernization where it prevents regressions

## Good Commands for Returning Users

- `help`
- `themer`
- `settings`
- `wallpaper -auto`
- `preset -ls`
- `apps -ls`
- `notifications -access`
- `debug -settings`
