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

### `guide`

Run a non-blocking walkthrough in the terminal. Active guide steps use the normal suggestion row for the current command and guide controls.

When a guide is active, it owns the suggestion row until `guide -off`. Saved progress survives launcher restarts and can be restored with `guide -resume`.

Useful forms:

- `guide`
- `guide -start basics`
- `guide -start customize`
- `guide -start modules`
- `guide -resume`
- `guide -restart basics`
- `guide -next`
- `guide -back`
- `guide -off`

### `restart`

Reload Re:T-UI and re-apply modified settings.

Use it after direct file edits or when you want to force a clean visual refresh.

### `refresh`

Refresh launcher-managed data such as apps, aliases, music, and contacts.

### `search`

Open a file-backed search provider. Providers live in `search.txt`, so the default providers and user-defined providers use the same registry.

Useful forms:

- `search -gg <query>` for Google
- `search -ps <app>` for Play Store
- `search -yt <query>` for YouTube
- `search -dd <query>` for DuckDuckGo
- `search -u <url>` for a URL
- `search -add <provider> <url_template>` to add or replace a provider
- `search -rm <provider>` to remove a provider
- `search -ls` to list providers
- `search -file` to edit `search.txt`
- `search -reset` to restore default providers

Template tokens:

- `{query}` URL-encodes the query
- `{query+}` URL-encodes the query and uses `+` for spaces
- `{slug}` replaces spaces with underscores before URL encoding
- `{raw}` inserts the query unchanged
- `{url}` opens a URL and adds `https://` if needed

Example:

`search -add sdw https://stardewvalleywiki.com/{slug}`

Then:

`search -sdw Ancient Fruit`

`install` remains as a deprecated compatibility command, but new flows should use `search -ps`.

## Settings and Theming

### `settings`

Open the Re:T-UI terminal-style settings hub.

Use this for:

- appearance
- behavior
- integrations
- fonts
- presets

Example:

- `settings`

`themer` remains as a hidden compatibility alias, but `settings` is the user-facing entry point.

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
- `notifications -prev`
- `notifications -next`
- `notifications -open`
- `notifications -reply`
- `notifications -file`

Notes:

- `notifications -on` and `notifications -off` control the notifications module.
- `terminal_notifications` in `notifications.xml` controls printing into the output terminal.
- `notifications -prev` and `notifications -next` page through the selected notification module item.
- `notifications -reply` starts a native prompt and replies to the selected notification when Android exposes a reply action.

### `reply`

Reply to supported notifications.

Useful if you want Re:T-UI to stay terminal-first for simple message responses.

Common commands:

- `reply -bind <app or package>`
- `reply -ls`
- `reply -to <app or package> <text>`

## Apps and App Drawer

### `apps`

Manage app visibility, groups, and drawer state.

Common commands:

- `apps -ls` opens the app drawer, or lists visible apps when the drawer UI is unavailable
- `apps -lsh` lists hidden apps
- `apps -l <app>`
- `apps -hide <app>`
- `apps -show <app>`
- `apps -st <app>`
- `apps -ps <app>`
- `apps -frc <app>`
- `apps -default_app <index> <app | most_used | null>`
- `apps -mkgp <group>`
- `apps -rmgp <group>`
- `apps -addtogp <group> <app>`
- `apps -rmfromgp <group> <app>`
- `apps -lsgp`
- `apps -gp_bg_color <group> <color>`
- `apps -gp_fore_color <group> <color>`
- `apps -tutorial`

Why this matters:

- typing an app name at the prompt is for launching
- app management is intentionally handled by explicit `apps` commands
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

### `alias`

Create and manage custom shortcut commands.

Common commands:

- `alias -add <name> <command content>`
- `alias -rm <name>`
- `alias -ls`
- `alias -file`

Aliases are one of the best ways to make Re:T-UI feel personal without giving up the command-line identity.

## Automation and Web

See also: [Automation and Chaining](./Automation-and-Chaining.md).

### `termux`

Open the Re:T-UI Termux console or dispatch a non-interactive Termux script.

Common commands:

- `termux`
- `termux -status`
- `termux -setup`
- `termux -open`
- `termux -run <script_path> [args...]`
- `termux -apps`
- `termux -app <id>`
- `termux -app-add <id> <command>`
- `termux -app-info <id>`
- `termux -app-sync <id>`
- `termux -app-actions <id>`
- `termux -app-action <id> <label> [input]`
- `termux -app-action-rm <id> <label>`
- `termux -app-rm <id>` or `termux -rm-app <id>`

Script aliases use the `-s` alias scope:

- `alias -add -s test /data/data/com.termux/files/home/retui/test.sh`
- `termux -run test`

Use this for scripts that print output and exit. Open Termux directly for interactive shells, editors, SSH sessions, and REPLs.

Termux apps are tmux-backed sessions shown inside the Re:T-UI Termux surface. Register one with `termux -app-add <id> <command>`, remove one with `termux -app-rm <id>`, then open it with `termux -app <id>`. Inside a Termux app, normal input is sent to the session; local commands use a colon prefix: `:refresh`, `:restart`, `:stop`, `:detach`, and `:open`.

Re:T-UI mirrors a small app manifest into `~/.retui/apps/<id>/app.json` when an app is registered or opened. Scripts launched through the app surface receive `RETUI_APP_ID`, `RETUI_APP_HOME`, `RETUI_APP_STATE`, and `RETUI_APP_MANIFEST`. Use `termux -app-info <id>` to inspect the local registration and `termux -app-sync <id>` to explicitly rewrite the Termux-side manifest. Static action chips can be added with `termux -app-action <id> <label> [input]`.

### `tbridge`

Inspect and set up the Termux bridge used by scripts, modules, callbacks, and automation.

Common commands:

- `tbridge -status`
- `tbridge -doctor`
- `tbridge -setup`
- `tbridge -probe`

TBridge is not the file browser. Use `files` for interactive file navigation, or `ls` / `open` / `share` with `file_backend=termux` for bridge-backed quick file actions.

### `module`

Show and manage built-in modules, Termux-backed modules, launcher-backed modules, and Lua modules.

Built-in modules:

- `music`
- `notifications`
- `timer`
- `calendar`
- `reminder`
- `notes`
- `rss`
- `weather`

Common commands:

- `module -ls`
- `module -show music`
- `module -show notifications`
- `module -show timer`
- `module -show calendar`
- `module -show reminder`
- `module -show notes`
- `module -show rss`
- `module -show weather`
- `module -prompt reminder add`
- `module -prompt reminder edit`
- `module -prompt reminder remove`
- `module -new lua counter`
- `module -edit counter`
- `module -config counter`
- `module -check counter`
- `module -approve counter`
- `module -export counter`
- `module -dock add notifications`
- `module -dock remove music`
- `module -add server termux:/data/data/com.termux/files/home/retui/server-health.sh`
- `module -refresh server`
- `module -rm server`
- `module -hide music`
- `module -dock add server`
- `module -dock remove music`
- `module -close`

The reminder module is the first native conversational module. It asks for text, date, time, and confirmation through the normal terminal input surface, then schedules an Android notification.

Design direction:

- modules are Re:T-UI-owned terminal panels, not Android widgets
- active modules can provide suggestion chips when input is empty
- Termux modules should stay text/callback based, with no arbitrary shell code loaded into Re:T-UI
- Lua modules use the launcher-native Lua runtime for safe panels, buttons, config, and app/intent/shortcut helpers
- reminder prompt sessions ask users for values step by step and keep normal command execution paused until the session ends

Termux modules use Termux for execution and render text back inside a Re:T-UI module window. Lua modules use app-local source files and the same dock. `module -rm` removes only Re:T-UI's registry entry; it does not delete the Termux script.

See also: [Modules](./Modules.md).

TBridge is no longer positioned as the file manager backend. Use `files` for file navigation.

### `lua`

Create and open launcher-native Lua apps. Lua apps use the same sandboxed runtime as Lua modules, but open in a focused terminal-styled surface with typed input and native `ui:render(...)` layout support.

Useful forms:

- `lua -apps`
- `lua -new app habit`
- `lua -app habit`
- `lua -edit habit`
- `lua -config habit`
- `lua -check habit`
- `lua -info habit`
- `lua -approve habit`
- `lua -export habit`
- `lua -disable habit`
- `lua -enable habit`

Inside a Lua app, normal input is delivered to `on_input(text)`. Local commands use a colon prefix: `:help`, `:refresh`, `:restart`, `:config`, `:edit`, `:clear`, and `:close`. Renderer buttons with `action` or `value` are delivered to `on_action(value)`.

### `widget`

Legacy aliases for Lua module package management. Prefer `module` for new work.

Useful forms:

- `widget -add counter`
- `widget -new counter`
- `widget -edit counter`
- `widget -rename counter better_counter`
- `widget -show counter`
- `widget -refresh counter`
- `widget -check counter`
- `widget -info counter`
- `widget -approve counter`
- `widget -copy-error counter`
- `widget -disable counter`
- `widget -enable counter`
- `widget -export counter`
- `widget -toggle counter`
- `widget -expand counter`
- `widget -collapse counter`
- `widget -rm counter`

Lua modules are saved under Re:T-UI's local `widgets/<id>/` storage folder for compatibility and register as `lua:<id>` modules. The document name is the module title; the id is the storage/command slug. Lua modules can expose indexed buttons, parameterized action chips, choice-dialog chips, direct command chips, expandable/collapsed render modes, active ticking, and clipboard export. Use `module -new lua <name>` to open the editor and paste shared Lua manually. Sensitive Lua capabilities require `-- permissions = "..."` metadata and `module -approve <id>` consent; this does not add Android manifest permissions. Runtime errors surface recovery chips for check/copy-error/disable.

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

### `files`

Open Re:T-UI Files, the companion terminal-style file console.

Examples:

- `files`

After it opens, use commands inside Re:T-UI Files:

- `open notes.txt`
- `share backup.zip`
- `cd configs`

Use the Files app for file navigation, opening, and sharing. The launcher passes theme, font, and margin values so the app can visually match Re:T-UI.

See also: [Re:T-UI Files](./ReTUI-Files.md).

### `config`

Directly inspect or change configuration values.

Useful forms:

- `config -file <file>`
- `config -get <option>`
- `config -set <option> <value>`
- `config -reset <option>`

This is the old-school power-user path.

### `tuixt`

Legacy launcher text-editor infrastructure.

It is kept for internal compatibility, but it is no longer the recommended file surface and should not appear as a general Android file editor.

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
- `files`
- `wallpaper -auto`
- `preset -save <name>`
- `preset -apply <name>`
- `alias -add <name> <command>`
- `alias -add -s <name> <script_path>`
- `notifications -access`
- `termux -setup`
- `tbridge -doctor`
- `apps -ls`
- `restart`
- `debug -settings`
