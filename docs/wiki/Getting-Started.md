# Getting Started

This page is for first-time setup and basic orientation.

## First Launch

When Re:T-UI opens, you are dropped into a terminal-style launcher. You can:

- type commands directly
- launch apps by name
- use suggestions under the input field
- open the settings hub with `themer` or `settings`

## Recommended First Steps

1. Give the prompt your own identity:
   `username <user> <device>`

2. Open the settings hub:
   `themer`

3. Pick or confirm a font in:
   `Appearance > Fonts`

4. Choose your wallpaper flow:
   - `wallpaper` for the normal wallpaper picker
   - `wallpaper -live` for live wallpapers

5. If you want Re:T-UI to derive colors from your wallpaper:
   `wallpaper -auto`

6. Save the result as a preset:
   `preset -save <name>`

## Basic Navigation

- `themer` opens the settings hub
- `settings -theme` jumps to appearance-related settings
- `settings -music` opens music settings
- `settings -notifications` opens behavior settings, including notification-related XML

## Important Concepts

### Commands

Commands are the fastest way to operate the launcher once you know them.

### Config Files

Power users can still edit launcher behavior through XML and text files in the Re:T-UI folder.

### Presets

Presets let you save a theme state and reuse it later.

### Auto Color

Auto color derives colors from the current wallpaper, but it is separate from manually saved presets.

## Good First Commands

- `help`
- `themer`
- `preset -ls`
- `wallpaper -auto`
- `notifications -access`

## Tip

If something visual does not refresh immediately after a major theme change, run:

`restart`
