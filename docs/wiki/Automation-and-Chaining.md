# Automation and Chaining

Re:T-UI has several automation surfaces. Aliases remain the main workflow layer.

## Current Surfaces

### Workflow Aliases

Aliases can chain normal Re:T-UI commands with the configured command separator. The default separator is `;`.

```text
alias -add focus module -show timer; timer 25m; notifications -off
```

Run it with:

```text
focus
```

Use `alias -ls` or `alias -file` to inspect workflows.

### Termux

Termux integration handles non-interactive script dispatch and script-backed modules.

```text
termux -run script-name
module -add server termux:/data/data/com.termux/files/home/retui/server.sh
module -refresh server
```

Termux remains the real shell. Re:T-UI dispatches scripts and renders returned output.

### Callbacks

Callbacks are token-gated and intentionally narrow.

Supported callback actions:

- `output`: print text to Re:T-UI output
- `notify`: print a notification-style callback line
- `module_set` / `module`: update a script module body

Callbacks do not accept arbitrary external command execution.

### Webhooks

Webhooks store POST targets and body templates.

```text
webhook -add deploy https://example.com/deploy "{\"env\":\"%1\"}"
webhook deploy prod
```

Use webhooks for explicit outbound calls, not hidden background automation.

### Android Shortcuts

App shortcuts can be listed and launched.

```text
shortcut -ls AppName
shortcut -use shortcutId AppName
```

### Notifications and Reply

Notification commands control filters, app inclusion, terminal notification display, and reply bindings.

```text
notifications -off
notifications -on
reply -bind Signal
reply -to Signal hello
```

### Clocks

Timers, stopwatch, and pomodoro provide visible time-based state. They are not general schedulers.

```text
timer 25m
timer -add 5m
stopwatch
pomodoro deep-work
```

## Product Rule

Automation should stay inspectable. Prefer aliases, scripts, callbacks, modules, and webhooks that the user can read and edit. Avoid hidden background behavior and avoid arbitrary external command execution.
