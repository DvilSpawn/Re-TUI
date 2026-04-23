# Customization and Config Files

Re:T-UI still supports direct file-based customization.

That means you can use the launcher like a normal user through the settings hub, or like a terminal gremlin with XML files and commands.

## Main Config Files

These files live in the Re:T-UI folder.

- `behavior.xml`
- `theme.xml`
- `ui.xml`
- `suggestions.xml`
- `toolbar.xml`
- `notifications.xml`
- `apps.xml`
- `cmd.xml`
- `alias.txt`
- `ascii.txt`
- `rss.xml`

## What Each File Does

### `theme.xml`

Controls the main visual palette:

- terminal colors
- borders
- widget colors
- overlays
- app drawer color

### `ui.xml`

Controls visual behavior and layout:

- font choices
- visibility of UI elements
- status line presentation
- wallpaper integration
- auto color toggle

### `suggestions.xml`

Controls suggestion row behavior and colors:

- apps
- aliases
- commands
- contacts
- files

### `behavior.xml`

Controls interaction and launcher behavior:

- keyboard behavior
- system actions
- music-related behavior
- command handling

### `notifications.xml`

Controls notification behavior:

- terminal widget visibility
- output printing
- formats
- include/exclude behavior
- filters

### `apps.xml`

Controls app-specific behavior and app groups.

## Recommended Workflow

For most users:

1. Use `themer`
2. Change settings through the terminal UI
3. Save clean visual states as presets

For power users:

1. Use `themer` to discover the setting
2. Edit the XML directly if needed
3. Save and reload

## Re:T-UI Approach

The project now treats the settings hub as the friendly front door and the files as the serious workshop in the back.

Both are valid.
