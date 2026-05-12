# Modules

Modules are Re:T-UI-owned terminal panels. They are meant to be small workstation instruments: status surfaces, quick controls, script output, and eventually guided input flows.

They are not arbitrary Android plugins and they do not load user Java/Kotlin code into the launcher.

For the phone-friendly walkthrough, use the hosted module guide: [Module Walkthrough](../modules.html).

## Current Module Commands

- `module -ls`
- `module -show music`
- `module -show notifications`
- `module -show timer`
- `module -show calendar`
- `module -show reminder`
- `module -prompt reminder add`
- `module -prompt reminder edit`
- `module -prompt reminder remove`
- `module -add server termux:/data/data/com.termux/files/home/retui/server-health.sh`
- `module -refresh server`
- `module -rm server`
- `module -hide music`
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

The dock is intentionally deliberate. Hiding a module from the dock does not delete it from the registry.

## Script Modules

Script-backed modules use Termux through TBridge. Re:T-UI runs the script, parses stdout, and renders a terminal panel.

Example:

```text
module -add server termux:/data/data/com.termux/files/home/retui/server-health.sh
module -refresh server
module -show server
```

Simple stdout becomes the module body.

## Prototype Lua Widgets

Lua widgets are an experimental in-app scripting surface for regular users who do not want to manage Termux files. They are saved in Re:T-UI's local `widgets/<id>/main.lua` folder, included in personal backup/restore, and registered as `lua:<id>` modules so they share the same module dock as built-ins and Termux modules.

Test commands:

```text
widget -samples
widget -new counter
widget -edit counter
widget -show counter
widget -refresh counter
widget -rm counter
```

The current prototype supports a deliberately small API:

- `on_load()`
- `on_resume()`
- `on_click(index)`
- `ui:set_title(text)`
- `ui:show_text(text)`
- `ui:show_lines(lines)`
- `ui:show_buttons(labels)`
- `ui:show_progress_bar(label, current, max)`

Button labels become module suggestion chips. Tapping a chip dispatches `widget -click <id> <index>`, which calls the widget's `on_click(index)` handler and repaints the active module.

This is a test surface, not the final marketplace runtime. The bundled samples are adapted from the AIO Launcher sample style and are intentionally dev-only.

## Launcher Variables For Scripts

Before Re:T-UI dispatches a script module to Termux, it materializes a small set of read-only launcher values and replaces `%RETUI_*` tokens in a temporary runtime copy of the script. The original script is not edited.

Available tokens:

- `%RETUI_CALENDAR_UPCOMING_MONTH` -> TSV file path with this month's upcoming events: `date<TAB>time<TAB>title<TAB>location`
- `%RETUI_BATTERY_JSON` -> JSON file path with battery percent and status code
- `%RETUI_NETWORK_JSON` -> JSON file path with connected, Wi-Fi, and mobile network type values
- `%RETUI_BRIGHTNESS_JSON` -> JSON file path with brightness percent and auto-brightness state
- `%RETUI_THEME_JSON` -> JSON file path with core theme colors
- `%RETUI_UI_JSON` -> JSON file path with module/output/header radius and header text sizes
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
::suggest refresh | command | module -refresh server
::suggest logs | command | termux -run logs
```

Current behavior:

- `::title` sets the module label.
- `::body` adds a body line.
- normal stdout becomes body text.
- `::suggest label | command | command text` adds suggestion chips while the module is active and input is empty.
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

Re:T-UI should own Android scheduling for reminder/alarm reliability. Scripts can request scheduling, but the launcher should handle notification channels, alarm timing, and tones.

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

Module controls are quiet actions. Tapping a module suggestion such as `prev`, `next`, `reply`, or `open` should execute the action without adding the backing command to output history. If the user types the same command manually, it should still appear in the terminal as normal input.

## Reply Sessions

Reply-capable modules should preserve the selected item while the user is typing. If a new item arrives mid-reply, it should not steal focus.

For notifications, the reply flow is:

1. select a notification in the module
2. run `notifications -reply` or tap the reply suggestion
3. Re:T-UI prompts for the reply text
4. Re:T-UI sends the text through Android inline reply using the selected notification action

While the reply prompt is active, previous/next navigation is disabled. This avoids sending a reply to the wrong conversation if another notification arrives before submit.

## Planned Power-User Improvements

- `module -inspect <name>`
- `module -logs <name>`
- `module -refresh-all`
- `module -refresh-dock`
- `module -interval <name> 60s|5m|off`
- `module -new <template>`
- `module -profile save <name>`
- `module -profile apply <name>`

The goal is inspectable power, not hidden automation.
