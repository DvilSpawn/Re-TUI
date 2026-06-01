# Re:T-UI Wiki

Welcome to the Re:T-UI wiki.

Re:T-UI is a modern continuation of the original T-UI launcher: command-first, deeply customizable, and designed to feel like a distraction-free Android workstation surface instead of a skin over stock Android.

This wiki is organized like the original upstream docs, but rebuilt around the current Re:T-UI feature set.

## Start Here

- [Getting Started](./Getting-Started.md)
- [Customization and Config Files](./Customization-and-Config-Files.md)
- [Settings Hub](./Settings-Hub.md)

## Reference and Guides

- [Command Reference](./Command-Reference.md)
- [Apps Command](./Apps.md)
- [Aliases](./Aliases.md)
- [Automation and Chaining](./Automation-and-Chaining.md)
- [Migration Guide](./Migration-Guide.md)
- [Preset Sharing](./Preset-Sharing.md)
- [Termux Integration](./Termux-Integration.md)
- [Lua Modules](./Lua-Widgets.md)
- [Modules](./Modules.md)
- [Re:T-UI Files](./ReTUI-Files.md)
- [FAQ and Troubleshooting](./FAQ-and-Troubleshooting.md)
- [Support and Release Channels](./Support-and-Release-Channels.md)

## Features

- [Presets and Wallpaper Auto Color](./Presets-and-Wallpaper-Auto-Color.md)
- [Notifications](./Notifications.md)
- [Music Visualizer](./Music-Visualizer.md)
- [Apps Command](./Apps.md)
- [App Drawer](./App-Drawer.md)
- [Modules](./Modules.md)
- [Re:T-UI Files](./ReTUI-Files.md)
- [Termux Integration](./Termux-Integration.md)
- [Automation and Chaining](./Automation-and-Chaining.md)

## Philosophy

Re:T-UI keeps the command line as the primary interface.

UI surfaces like the app drawer and settings hub exist to make the launcher easier to live with, but they are helpers. The identity of the launcher is still:

- typed commands first
- visible config files for power users
- local, inspectable workflows
- strong theming
- playful terminal aesthetics

## Main Commands Worth Knowing

These are public current-build commands. Hidden compatibility aliases are intentionally left out.

- `help`
- `settings`
- `guide`
- `apps -ls`
- `files`
- `module -ls`
- `module -new lua <name>`
- `preset -save <name>`
- `preset -apply <name>`
- `wallpaper -static`
- `wallpaper -live`
- `wallpaper -auto`
- `notifications -access`
- `notifications -on`
- `termux -setup`
- `tbridge -doctor`

## Notes

- Most launcher settings still live in the Re:T-UI folder as editable files.
- The settings hub is the recommended entry point for normal setup.
- Commands remain the preferred way to move quickly once you know your setup.
