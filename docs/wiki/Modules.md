# Modules

Modules are Re:T-UI-owned terminal panels. They are small workstation instruments: status surfaces, quick controls, script output, and guided input flows.

They are not arbitrary Android plugins and they do not load user Java/Kotlin code into the launcher.

For the phone-friendly walkthrough, use the hosted module guide: [Module Walkthrough](../modules.html).

## Current Module Commands

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
- `module -check counter`
- `module -approve counter`
- `module -add server termux:/data/data/com.termux/files/home/retui/server-health.sh`
- `module -refresh server`
- `module -rm server`
- `module -hide music`
- `module -dock -toggle`
- `module -dock add server`
- `module -dock remove music`
- `module -close`

## Built-In Modules

Current built-ins:

- `music`
- `notifications`
- `timer`
- `calendar`
- `reminder`
- `notes`
- `rss`
- `weather`

The dock is intentionally deliberate. Hiding a module from the dock does not delete it from the registry.
Removing every module from the dock now leaves the dock intentionally empty instead of restoring the default module list. To hide or show the dock row live, run `module -dock -toggle`. You can also turn off `show_module_dock` in Behavior settings or run `config -set show_module_dock false`; modules remain available through `module -show`, `module -dock add`, and related commands.

New installs keep the default dock focused on `music`, `notifications`, `timer`, `calendar`, and `reminder`. Add `notes`, `rss`, or `weather` when you want those panels visible in the dock.

When an active module exposes actions, Re:T-UI shows those actions in their own compact strip above the input. The normal suggestion strip stays available for apps, aliases, shortcuts, guide actions, and commands, so tapping back into the input returns regular suggestions without closing the module.

## Script Modules

Script-backed modules use Termux through TBridge. Re:T-UI runs the script, parses stdout, and renders a terminal panel.

Example:

```text
module -add server termux:/data/data/com.termux/files/home/retui/server-health.sh
module -refresh server
module -show server
```

Simple stdout becomes the module body and uses the active launcher font. When a script needs fixed-width alignment, it must opt in explicitly:

```sh
echo "::ascii"
curl -s 'wttr.in/Chennai?0T'
echo "::end"
```

`::pre`, `::ascii`, and `::code` start a monospace block. `::end` closes it. One-line forms are also valid: `::pre text`, `::ascii text`, and `::code text`.

## Lua Modules

Lua modules are an in-app scripting surface for regular users who do not want to manage Termux files. They are saved in Re:T-UI's local `widgets/<id>/main.lua` storage folder for compatibility, included in personal backup/restore, and register as `lua:<id>` modules so they share the same module dock as built-ins and Termux modules.

Test commands:

```text
module -new lua counter
module -edit counter
module -rename counter better_counter
module -show counter
module -refresh counter
module -check counter
module -info counter
module -approve counter
module -export counter
module -toggle counter
module -expand counter
module -collapse counter
```

The Lua runtime supports launcher-native text/layout output, module buttons, suggestion chips, config forms, persistent `prefs`, module-local `files`, JSON, async HTTP callbacks, app/intent/shortcut helpers, active ticking, Lua suggestion scripts, clipboard export, and launcher/system helpers. See the full scripting reference: [Lua Modules](./Lua-Widgets.md).

Lua permissions are script-level consent only. Scripts that use `network`, `clipboard`, `vibrate`, `local-files`, `active-tick`, `notifications`, `apps`, `intents`, or `shortcuts` must declare `-- permissions = "..."` and be approved with `module -approve <id>`. Re:T-UI does not add Android manifest permissions for Lua.

Lua scripts can declare `-- retui = "1"` for API compatibility. Runtime failures show recovery controls, and `module -disable <id>` parks a broken script without deleting it.

Module button APIs render native controls in the module panel only. `ui:show_buttons` dispatches `module -click <id> <index>` into `on_click(index)`, while `ui:show_action(label, value)` dispatches `module -action <id> <value>` into `on_action(value)`. Use `ui:suggest_action`, `ui:suggest_command`, `ui:suggest_module`, or `ui:suggest_text` when the same module should keep its panel clean and expose controls as suggestion-row chips instead.

Active Lua modules refresh when they are shown. Tapping the module title forces a refresh, so scripts no longer need to add a default refresh chip just to keep state current. Manual `module -refresh <id>` still works for explicit rerenders. The older `widget` command remains as a compatibility alias for existing shared Lua packages.

## Launcher Variables For Scripts

Before Re:T-UI dispatches a script module to Termux, it materializes a small set of read-only launcher values and replaces `%RETUI_*` tokens in a temporary runtime copy of the script. The original script is not edited.

Available tokens:

- `%RETUI_CALENDAR_UPCOMING_MONTH` -> TSV file path with this month's upcoming events: `date<TAB>time<TAB>title<TAB>location`
- `%RETUI_BATTERY_JSON` -> JSON file path with battery percent and status code
- `%RETUI_NETWORK_JSON` -> JSON file path with connected, Wi-Fi, and mobile network type values
- `%RETUI_BRIGHTNESS_JSON` -> JSON file path with brightness percent and auto-brightness state
- `%RETUI_THEME_JSON` -> JSON file path with core theme colors
- `%RETUI_UI_JSON` -> JSON file path with module/output/header radius plus module/output text sizes
- `%RETUI_STORAGE_JSON` -> JSON file path with shared-storage total/free/used byte counts
- `%RETUI_NOW` -> current Unix time in milliseconds

The same file-path tokens are also exported to Termux without the `%` prefix, for example:

```sh
cat "$RETUI_CALENDAR_UPCOMING_MONTH"
```

Use token replacement when you want the script to remain portable and readable:

```sh
EVENTS_FILE="%RETUI_CALENDAR_UPCOMING_MONTH"
```

Sensitive data stays narrow. Re:T-UI currently materializes read-only values only; contacts, notification replies, precise location, and arbitrary action APIs should not be exposed as general script variables.

## Editable Upcoming Events Example

Create `~/retui/events.sh` in Termux:

```sh
#!/data/data/com.termux/files/usr/bin/sh

echo "::title Events"

EVENTS_FILE="%RETUI_CALENDAR_UPCOMING_MONTH"
if [ ! -s "$EVENTS_FILE" ]; then
  echo "::body No upcoming events this month."
else
  while IFS='	' read -r date time title location; do
    if [ -n "$time" ]; then
      line="$date $time - $title"
    else
      line="$date - $title"
    fi
    [ -n "$location" ] && line="$line @ $location"
    echo "::body $line"
  done < "$EVENTS_FILE"
fi

echo "::suggest refresh | command | module -refresh events"
echo "::suggest access | command | events -access"
echo "::suggest calendar | command | intent -view content://com.android.calendar/time/%RETUI_NOW"
```

Then register it:

```text
module -add events termux:/data/data/com.termux/files/home/retui/events.sh
module -refresh events
module -show events
```

If the module is empty, grant calendar permission:

```text
events -access
```

## Metadata Contract

Script modules can emit structured lines:

```text
::title Server
::body prod-api ONLINE
::pre CPU  [####....] 50%
::suggest refresh | command | module -refresh server
::suggest logs | command | termux -run logs
```

Current behavior:

- `::title` sets the module label.
- `::body` adds a body line.
- normal stdout becomes body text.
- `::pre`, `::ascii`, and `::code` mark monospace blocks for terminal art, tables, and code; `::end` returns to normal themed text.
- `::pre text`, `::ascii text`, and `::code text` add one monospace line without opening a longer block.
- `::suggest label | command | command text` adds action chips to the separate module strip while the module is active.
- `termux-run` and `callback` are reserved modes; only `command` suggestions execute today.

## Native Module Sessions

Module sessions let a supported module temporarily own the input loop.

The first shipped native session is the reminder module:

```text
module -prompt reminder add
> What do you want to be reminded about?
$ dental appointment

> What date?
$ 10/05/2026

> What time?
$ 11:30PM

> Confirm?
[save] [edit] [cancel]
```

While a session is active, normal command execution pauses until the user saves, edits, or cancels the prompt.

Current reminder actions:

- `module -prompt reminder add`
- `module -prompt reminder edit`
- `module -prompt reminder remove`
- `module -show reminder`

The reminder module stores reminders locally and schedules native Android notifications.

Prompt types used today:

- `text`
- `date`
- `time`
- `confirm`

Re:T-UI owns reminder scheduling, notification channels, alarm timing, and tones. Scripts should request reminder behavior through launcher-owned surfaces instead of trying to own Android alarm reliability.

## Pager Modules

Some modules show a list but should feel like a single focused panel. For those modules, Re:T-UI can own pagination instead of asking a script to keep track of UI state.

The notification module uses this pattern:

- the module shows one selected notification at a time
- `notifications -prev` moves to the previous notification
- `notifications -next` moves to the next notification
- `notifications -open` opens the selected notification
- `notifications -reply` starts a native reply prompt when the selected notification is replyable

The module pane is split vertically:

- top 80%: selected item content
- bottom 20%: previous and next controls, split 50/50

This keeps navigation visible without shrinking the message body too much.

Module controls are quiet actions. Tapping a module suggestion such as `prev`, `next`, `reply`, or `open` executes the action without adding the backing command to output history. If the user types the same command manually, it still appears in the terminal as normal input.

## Reply Sessions

Reply-capable modules preserve the selected item while the user is typing. If a new item arrives mid-reply, it does not steal focus.

For notifications, the reply flow is:

1. select a notification in the module
2. run `notifications -reply` or tap the reply suggestion
3. Re:T-UI prompts for the reply text
4. Re:T-UI sends the text through Android inline reply using the selected notification action

While the reply prompt is active, previous/next navigation is disabled. This avoids sending a reply to the wrong conversation if another notification arrives before submit.
