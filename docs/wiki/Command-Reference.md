# Command Reference

This page collects the commands most users will actually touch in Re:T-UI.

It is not meant to replace `help`, but to give you a reliable map of the command surface that matters most in day-to-day use.

## Core Launcher

### `help`

Show available commands or get help for one command.

Examples:

- `help`
- `help preset`
- `help notifications`

### `restart`

Reload Re:T-UI and re-apply modified settings.

Use it after direct file edits or when you want to force a clean visual refresh.

### `refresh`

Refresh launcher-managed data such as apps, aliases, music, and contacts.

## Settings and Theming

### `themer`

Open the Re:T-UI terminal-style settings hub.

Best entry point for:

- appearance
- behavior
- integrations
- fonts
- presets

### `settings`

Open the settings hub, or jump straight to a section.

Examples:

- `settings`
- `settings -appearance`
- `settings -behavior`
- `settings -integrations`
- `settings -system`

### `wallpaper`

Open the normal static wallpaper picker.

Related commands:

- `wallpaper -static`
- `wallpaper -live`
- `wallpaper -auto`

### `wallpaper -live`

Open the Android live wallpaper chooser.

### `wallpaper -auto`

Enable or refresh wallpaper-derived theme colors.

This is the quickest way to let Re:T-UI build a matching palette from the current wallpaper.

Current flow:

1. Set wallpaper
2. Run `wallpaper -auto`
3. Confirm the safety prompt
4. Save the result as a preset if you want to keep it

### `preset`

Re:T-UI’s current theme snapshot system.

Commands:

- `preset -save <name>`
- `preset -apply <name>`
- `preset -ls`

Use presets when you want a stable, reusable theme state.

### `theme`

Legacy upstream theme command.

It still exists for compatibility, but presets are the recommended path in Re:T-UI.

## Notifications

### `notifications`

Manage notification behavior and notification terminal visibility.

Most important forms:

- `notifications -access`
- `notifications -on`
- `notifications -off`
- `notifications -inc <app>`
- `notifications -exc <app>`
- `notifications -add_filter <id> <pattern>`
- `notifications -rm_filter <id>`
- `notifications -file`

Notes:

- `notifications -on` and `notifications -off` control the notification terminal widget.
- `terminal_notifications` in `notifications.xml` controls printing into the output terminal.

### `reply`

Reply to supported notifications.

Useful if you want Re:T-UI to stay terminal-first for simple message responses.

## Apps and App Drawer

### `apps`

Manage app visibility, groups, and drawer state.

Common commands:

- `apps -ls`
- `apps -lsh`
- `apps -hide <app>`
- `apps -show <app>`
- `apps -mkgp <group>`
- `apps -rmgp <group>`
- `apps -addtogp <group> <app>`
- `apps -rmfromgp <group> <app>`
- `apps -lsgp`

Why this matters:

- hidden apps stay out of the drawer
- groups feed the left-side app drawer tabs
- the drawer is not just visual; it reflects command-level organization

## Music

### `music`

Control music playback and inspect tracks.

Common commands:

- `music -play`
- `music -stop`
- `music -next`
- `music -previous`
- `music -info`

Re:T-UI also supports a preferred music app setting through the settings hub.

## Prompt and Identity

### `username <user> <device>`

Change the terminal identity shown in the prompt.

This is one of the fastest ways to make the launcher feel like yours.

## Automation and Web

### `webhook`

Create and trigger saved webhooks.

Common commands:

- `webhook -add <name> <url> <body_template>`
- `webhook -rm <name>`
- `webhook -ls`
- `webhook <name> <args...>`

### `post <url> <body>`

Send a raw HTTP POST request.

Useful for quick tests when you do not need a saved webhook.

## Files and Direct Config

### `config`

Directly inspect or change configuration values.

Useful forms:

- `config -file <file>`
- `config -get <option>`
- `config -set <option> <value>`
- `config -reset <option>`

This is the old-school power-user path.

### `tuixt`

Open the launcher text editor.

Useful when you want to edit text files from inside Re:T-UI.

## Inspection and Troubleshooting

### `debug`

Inspect runtime state that is otherwise hard to see.

Useful forms:

- `debug -settings`
- `debug -theme`
- `debug -presets`

This is the best command when a setting looks correct in XML but behaves differently at runtime.

## Practical Starter Set

If you only memorize a handful of commands, make it these:

- `themer`
- `settings`
- `wallpaper -auto`
- `preset -save <name>`
- `preset -apply <name>`
- `notifications -access`
- `apps -ls`
- `restart`
- `debug -settings`
