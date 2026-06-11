# Termux Integration

Re:T-UI can dispatch Termux scripts from its own terminal surface.

Termux remains the real shell. Re:T-UI can open a launcher-styled Termux console, dispatch scripts, host tmux-backed app sessions, and show an opt-in tmux workspace surface, but it does not replace Termux as the Linux environment.

TBridge is the Termux capability bridge for scripts, modules, callbacks, and automation diagnostics. Re:T-UI Files owns file navigation.

The old BusyBox manager has been removed; use Termux for Linux packages and maintained command-line tools.

## Commands

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
- `termux -app-action <id> <label> [input]`
- `termux -app-rm <id>`
- `tmux`
- `tmux launch <id>`
- `tmux switch <window>`
- `config -set show_tmux_workspace_button true`

TBridge diagnostics:

- `tbridge -status`
- `tbridge -doctor`
- `tbridge -setup`
- `tbridge -probe`

Inside the Termux console, you can also type:

- `status`
- `setup`
- `open`
- `run <script_path> [args...]`
- `apps`
- `app <id>`
- `app-add <id> <command>`
- `app-info <id>`
- `app-sync <id>`
- `app-action <id> <label> [input]`
- `app-rm <id>`
- `clear`
- `exit`

## Setup

Run:

`termux -setup`

Re:T-UI will print the setup checklist.

In Termux, enable external commands:

```sh
mkdir -p ~/.termux
echo 'allow-external-apps = true' >> ~/.termux/termux.properties
termux-reload-settings
```

Use a stable script folder:

```sh
mkdir -p ~/retui
nano ~/retui/test.sh
chmod +x ~/retui/test.sh
```

Example test script:

```sh
#!/data/data/com.termux/files/usr/bin/sh
echo "termux is working"
date
```

## Tmux Workspace

The tmux workspace is an optional launcher page for persistent Termux TUI sessions. It is hidden by default so regular users do not see an advanced terminal control unless they opt in.

Install the Termux-side `retui` helper from Termux:

```sh
pkg install curl tmux socat -y
curl -fsSL https://raw.githubusercontent.com/DvilSpawn/Re-TUI/master/termux/retui/install.sh | sh
```

Then enable the toolbar button in Re:T-UI:

```text
config -set show_tmux_workspace_button true
```

You can also enable it from Behavior settings. The button appears in the launcher toolbar after the setting is enabled.

What lives where:

- Termux owns the shell, tmux session, running commands, and network access.
- Re:T-UI owns the visual surface, input field, key tray, workspace chrome, and local gestures.
- The bridge stays local. Re:T-UI bootstraps through Termux `RUN_COMMAND`, then talks to the `retui` helper over a token-protected local socket when available.

Inside the workspace:

- use the key tray for `ESC`, `TAB`, arrows, page keys, `CTRL`, `ALT`, `SHIFT`, function keys, insert/delete, enter, and backspace
- swipe the key tray horizontally to switch between NAV and function-key modes
- swipe the terminal pane horizontally to move between tmux windows
- use `:help`, `:new [name]`, `:prev`, `:next`, `:refresh`, `:status`, `:reconnect`, and `:home` for local workspace commands

Workspace v2 adds quick launchers without changing ownership of the process:

```text
:launch
:launch htop
:launch mc
:save notes nano ~/notes.txt
:rm notes
```

Built-in launchers include `shell`, `htop`, `mc`, `nano`, `vim`, `python`, `node`, and `logs`. Saved launchers are Re:T-UI preferences that create a named tmux window and send the configured command into Termux. Termux still owns the shell, installed packages, and command execution. If a launcher command is missing, install it in Termux with `pkg`.

The same workspace actions are available from the main launcher prompt with the `tmux` command:

```text
tmux
tmux launch mc
tmux launch logs
tmux switch 2
tmux switch 2:bash
tmux status
tmux reconnect
```

That means normal launcher aliases can jump straight into workspace tools:

```text
alias -add MC tmux launch mc
alias -add LOGS tmux launch logs
alias -add BASH2 tmux switch 2
```

Use launcher ids such as `mc` and `logs` when you want to open a tool. Use a tmux window index such as `2`, or the visible token `2:bash`, when you want to jump to an already-open tmux window.

Use `:status` to check Termux install state, `RUN_COMMAND` availability, socket connection state, current geometry, and saved launcher count. Use `:reconnect` if the live socket stream is stale after Termux restarts.

The workspace is the best Re:T-UI surface for tools such as Midnight Commander, htop, SSH, REPLs, and other TUIs that need persistent tmux state. For one-shot scripts that print output and exit, keep using `termux -run`.

## Script Aliases

Script aliases use the `-s` alias scope.

Example:

`alias -add -s test /data/data/com.termux/files/home/retui/test.sh`

Then run:

`termux -run test`

The `-s` scope keeps script aliases separate from normal home-screen aliases.

## Script Modules

Script modules let a Termux script feed one of Re:T-UI's terminal-style module panels.

Add a script-backed module:

```text
module -add server termux:/data/data/com.termux/files/home/retui/server-health.sh
```

Refresh it manually:

```text
module -refresh server
```

Show or dock it:

```text
module -show server
module -dock add server
module -dock remove server
```

Remove it from Re:T-UI's module registry:

```text
module -rm server
```

`module -rm` does not delete the Termux script file. It only removes Re:T-UI's registry entry.

For simple modules, printing text to stdout is enough. Re:T-UI will use stdout as the module body after `module -refresh`, and that normal text follows the active launcher font.

Use explicit preformatted markers when fixed-width alignment matters:

```sh
echo "::ascii"
curl -s 'wttr.in/Chennai?0T'
echo "::end"
```

`::pre`, `::ascii`, and `::code` start a monospace block. `::end` closes it. One-line forms such as `::pre CPU  [####....] 50%`, `::ascii ...`, and `::code echo ok` add one monospace line without opening a longer block.

Re:T-UI can also resolve launcher-owned `%RETUI_*` variables before the script reaches Termux. This lets a normal editable Termux script consume safe launcher data without giving Termux broad Android-provider access.

Read-only module variables:

- `%RETUI_CALENDAR_UPCOMING_MONTH`
- `%RETUI_BATTERY_JSON`
- `%RETUI_NETWORK_JSON`
- `%RETUI_BRIGHTNESS_JSON`
- `%RETUI_THEME_JSON`
- `%RETUI_UI_JSON`
- `%RETUI_STORAGE_JSON`
- `%RETUI_NOW`

Most variables resolve to file paths under shared storage. Read them with `cat`, `awk`, `sed`, or any normal shell tool.

Example:

```sh
#!/data/data/com.termux/files/usr/bin/sh

STATUS="$(ping -c 1 8.8.8.8 >/dev/null 2>&1 && echo 'SERVER: ONLINE' || echo 'SERVER: OFFLINE')"
TIME="$(date)"

printf '%s\n%s\n' "$STATUS" "$TIME"
```

Upcoming-events example:

```sh
#!/data/data/com.termux/files/usr/bin/sh

echo "::title Events"

EVENTS_FILE="%RETUI_CALENDAR_UPCOMING_MONTH"
if [ ! -s "$EVENTS_FILE" ]; then
  echo "::body No upcoming events this month."
else
  while IFS='	' read -r date time title location; do
    [ -n "$time" ] && echo "::body $date $time - $title" || echo "::body $date - $title"
  done < "$EVENTS_FILE"
fi

echo "::suggest refresh | command | module -refresh events"
echo "::suggest access | command | events -access"
```

You can also update modules by callback. This is better for scripts that run on their own schedule.

## Termux Apps

Termux apps are persistent session surfaces for interactive Bash tools that should feel closer to an application than a one-shot module. Re:T-UI owns the app window, input field, styling, suggestions, and local commands. Termux owns the running process through `tmux`.

Common commands:

```text
termux -apps
termux -app-add myapp bash ~/retui/myapp.sh
termux -app myapp
termux -app-info myapp
termux -app-sync myapp
termux -app-actions myapp
termux -app-action myapp "restart service" r
termux -app-rm myapp
```

Re:T-UI owns app registration. A cloned shell project does not need to include a Re:T-UI manifest. When an app is registered or opened, Re:T-UI mirrors metadata into:

```text
~/.retui/apps/<id>/app.json
~/.retui/apps/<id>/state.json
~/.retui/apps/<id>/memory/
~/.retui/apps/<id>/logs/
```

The tmux session is launched with these environment variables:

```text
RETUI_APP_ID
RETUI_APP_HOME
RETUI_APP_STATE
RETUI_APP_MANIFEST
```

Scripts can use `RETUI_APP_STATE` for their own JSON state and `RETUI_APP_HOME` for persistent files. `termux -app-sync <id>` rewrites this manifest on demand and reports the sync result in the console. This does not add Android manifest permissions; it uses the existing Termux RUN_COMMAND bridge.

Static app actions are RetUI-owned command chips. They send fixed input into the tmux session, then refresh the captured pane. Custom apps can add actions with:

```text
termux -app-action myapp "show status" 6
termux -app-action myapp "continue"
```

Inside the app surface:

```text
7
:refresh
:restart
:stop
:detach
:open
```

Plain input is sent into the tmux session and followed by Enter. Colon-prefixed input is handled by Re:T-UI. This bridge keeps sessions alive, mirrors a RetUI-owned manifest, exposes static actions, and lets Re:T-UI capture the current tmux pane. It is not a full streaming PTY. Captures are sequenced so stale RunCommand results cannot overwrite newer frames, and refresh polling backs off unless the captured pane is still changing.

### Codex CLI Custom App

Codex CLI works well as a plain Termux app after it is installed inside Termux. Register the normal CLI directly so Re:T-UI owns the outer tmux session:

```text
termux -app-add codex codex
termux -app-add codex-resume codex resume --last
termux -app codex
```

Do not wrap this with a second tmux launcher such as `ccva-tmux-run`; Re:T-UI already keeps the session persistent. After opening the app, complete the Codex sign-in flow in the pane. Plain typed prompts are sent to Codex and submitted with Enter. Use `:refresh` if you want to force a pane capture, `:detach` to leave the session running, and `:stop` to kill the session.

Voice-specific Codex Android helpers, realtime audio, and shim APK setup are separate from the plain CLI app. Keep those out of the default Re:T-UI custom app unless the user has intentionally installed and validated the Android audio stack.

## TBridge Role

Use TBridge for:

- Termux health checks
- RUN_COMMAND permission diagnostics
- script runtime support
- script-module refreshes
- callback/token tests

Do not use TBridge as the primary file manager. Use:

```text
files
```

The older `tbridge -ls`, `tbridge -dirs`, and `tbridge -files` entry points are retired from the public command surface. If you want bridge-backed quick file actions, use `ls`, `open`, or `share` with `file_backend=termux`; if you want browsing, use `files`.

## Module Output

Termux modules print text and optional metadata lines. Re:T-UI parses those lines into a module title, body, monospace blocks, and suggestion chips.

```text
::title Timer
::body 25:00 ready
::suggest +5 minutes | command | timer -add 5m
```

Example:

```text
::title Server
::body prod-api ONLINE
::pre CPU  [####....] 50%
::suggest refresh | command | module -refresh server
::suggest logs | command | termux -run logs
```

- `::title` changes the module label.
- `::body` adds a line to the rendered module body.
- normal stdout lines also become module body text.
- `::pre`, `::ascii`, and `::code` mark monospace blocks for terminal art, tables, and code; `::end` returns to normal themed text.
- `::pre text`, `::ascii text`, and `::code text` add one monospace line.
- `::suggest label | command | command text` adds an active suggestion when that module is selected and the input is empty.
- `termux-run` and `callback` are reserved contract modes, but suggestion clicks currently execute `command` mode only.

## Native Module Prompt Sessions

Modules can ask for values from the user when they own a native prompt session.

Example:

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

The first shipped prompt session is the built-in reminder module. It stores reminders locally and schedules native Android notifications from Re:T-UI.

The first prompt types are:

- `text`
- `date`
- `time`
- `confirm`

Scripts should request Android reminders/alarms through Re:T-UI. Re:T-UI owns notification channels, alarm scheduling, and tones for reliability.

## Arguments

Arguments after the script are passed through to Termux.

Example:

`termux -run test hello world`

## Good Uses

- quick health checks
- backup scripts
- local file summaries
- curl checks
- server status scripts
- small automation helpers

## Limits

- Use non-interactive scripts.
- Do not run `vim`, `nano`, `ssh`, or REPL sessions through `termux -run`.
- Open Termux directly for interactive work.
- Long-running background work may need Android battery optimization disabled for Termux.

## Permission Notes

Termux must be installed from a current source such as F-Droid or GitHub. Old Play Store Termux builds may not expose the run-command bridge.

Android may ask for permission before Re:T-UI can run commands in Termux. Allow Re:T-UI, then retry the command.

## Callback Status

Callbacks from Termux, Tasker, or another automation app can be enabled with a token.

Token commands:

- `retui-token -status`
- `retui-token -show`
- `retui-token -rotate`
- `retui-token -off`

Broadcast action:

`com.dvil.tui_renewed.RETUI_CALLBACK`

Required extras:

- `token`
- `action`

Optional extras:

- `text`
- `title`
- `module`

Safe actions currently accepted:

- `output`
- `notify`
- `module_set`

Example from Termux:

```sh
am broadcast \
  -p com.dvil.tui_renewed \
  -a com.dvil.tui_renewed.RETUI_CALLBACK \
  --es token "YOUR_TOKEN" \
  --es action output \
  --es text "Backup complete"
```

Re:T-UI does not accept arbitrary external command execution through callbacks.

## Callback Module Example

```sh
#!/data/data/com.termux/files/usr/bin/sh

TOKEN="PASTE_RETUI_TOKEN_HERE"
MODULE="server"

STATUS="$(ping -c 1 8.8.8.8 >/dev/null 2>&1 && echo 'SERVER: ONLINE' || echo 'SERVER: OFFLINE')"
TIME="$(date)"

am broadcast \
  -p com.dvil.tui_renewed \
  -a com.dvil.tui_renewed.RETUI_CALLBACK \
  --es token "$TOKEN" \
  --es action module_set \
  --es module "$MODULE" \
  --es text "$STATUS
$TIME"
```

Get the token from Re:T-UI:

```text
retui-token -show
```

## Optional Termux Helper

Create `~/retui/retui-helper.sh`:

```sh
#!/data/data/com.termux/files/usr/bin/sh

RETUI_PACKAGE="com.dvil.tui_renewed"
RETUI_ACTION="com.dvil.tui_renewed.RETUI_CALLBACK"
RETUI_TOKEN="PASTE_RETUI_TOKEN_HERE"

retui_output() {
  am broadcast \
    -p "$RETUI_PACKAGE" \
    -a "$RETUI_ACTION" \
    --es token "$RETUI_TOKEN" \
    --es action output \
    --es text "$*"
}

retui_module() {
  module="$1"
  shift
  am broadcast \
    -p "$RETUI_PACKAGE" \
    -a "$RETUI_ACTION" \
    --es token "$RETUI_TOKEN" \
    --es action module_set \
    --es module "$module" \
    --es text "$*"
}
```

Use it:

```sh
. "$HOME/retui/retui-helper.sh"
retui_module server "SERVER: ONLINE
$(date)"
```

## Tasker Callback Example

Use Tasker's **Send Intent** action:

- Action: `com.dvil.tui_renewed.RETUI_CALLBACK`
- Package: `com.dvil.tui_renewed`
- Target: Broadcast Receiver
- Extra: `token:YOUR_TOKEN`
- Extra: `action:module_set`
- Extra: `module:server`
- Extra: `text:SERVER: ONLINE`

Use `action:output` to print to Re:T-UI output instead of updating a module.
