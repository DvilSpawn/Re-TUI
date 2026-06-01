# Re:T-UI Files

Re:T-UI Files is the companion file console for Re:T-UI.

The launcher stays the command desk. Re:T-UI Files owns file navigation, opening, sharing, and the visual file tree.

## Launcher Command

Open it from Re:T-UI:

```text
files
```

The launcher passes the current path and visual parameters to the Files app:

- theme text color
- border color
- terminal background
- font
- input font size
- display margin

This keeps the two apps visually aligned without forcing file-management complexity into the launcher.

## Current Files App Behavior

The Files app provides:

- terminal-style file tree
- click-to-expand directories
- click-to-open files
- Android `Open with` / share integration
- command input for file navigation
- folder/file suggestions
- bundled Nerd Font symbols for stable file icons

Useful commands inside the Files app:

- `cd <folder>`
- `cd ..`
- `ls`
- `tree`
- `open <file>`
- `share <file>`
- `permission`
- `refresh`
- `exit`

## Boundary With TBridge

Use `files` for file navigation.

Use `tbridge` for Termux scripts, modules, callbacks, and automation diagnostics.

Older TBridge file listing commands may remain for debug use, but they are not the main file manager path.

## Boundary With TUIXT

TUIXT is legacy launcher editor infrastructure. It should not appear as a general Android file editor.
