# Re:T-UI Termux Bridge

`retui` 0.3.4 is the Termux-side helper for the launcher's tmux workspace.
Termux owns the shell, tmux session and persistent jobs. Re:T-UI displays
captured cells and forwards input; this is not a second terminal runtime or a
claim of complete terminal-emulator fidelity.

## Install

Install this helper together with the matching launcher:

```sh
pkg install curl tmux socat -y
curl -fsSL https://raw.githubusercontent.com/DvilSpawn/Re-TUI/master/termux/retui/install.sh | sh
retui version
retui bridge status
```

For a development branch, replace `master` in the URL or set
`RETUI_INSTALL_BASE_URL` to its `termux/retui` directory before installation.
Termux must expose `RUN_COMMAND`, enable `allow-external-apps`, and grant the
launcher its RUN_COMMAND permission. The launcher still bootstraps through this
service. The socket is local IPC: an expected abstract Android socket, with
only `127.0.0.1` TCP fallback when cross-app abstract sockets are blocked.

## Workspace controls

- Tap a window label to select its index. The active window is highlighted.
- Swipe the terminal horizontally, or use `:prev` / `:next`, for adjacent windows.
- `:switch <window>` selects an index/name inside the pane. `tmux switch <window>`
  does the same from Launcher Home. `:new [name]` creates a window.
- `:launch <id>` focuses/creates a saved launcher window; `:save <id> <command>`
  saves a custom launcher and `:rm <id>` removes that saved entry.
- `:status`, `:refresh`, `:reconnect`, `:help` and `:home` remain local controls.
  Use the existing `termuxopen` command when the full Termux application is needed.
- Connecting/Ready/Disconnected belongs to the header, not the running TUI.
  Typing/key progress does not replace a captured editor or other application.
- Leaving the pane or backgrounding closes its stream, not tmux jobs. Returning
  reconnects to the same session. A local colon-command draft is retained.

Ready requires the helper's successful HELLO response and a complete valid
frame from the expected session. It does **not** acknowledge each input command:
this helper has no per-command ACK. Writes are bounded, and rejected/ambiguous
input is never automatically retried through a different mutation path. Check
the terminal before manually resending after a disconnect.

## Helper commands

```text
retui bridge capabilities
retui bridge status
retui bridge capture
retui bridge socket-start
retui bridge socket-daemon
retui bridge tcp-daemon
retui bridge socket-client
retui bridge send
retui bridge type
retui bridge new
retui bridge launch
retui bridge switch
retui bridge select
retui bridge key
retui bridge kill
```

`capabilities` is read-only and lists supported operation names. The launcher
chooses helper or direct tmux **before** mutation; it exits with the selected
helper's result rather than replaying failed input. Older helpers without the
capability query use direct tmux snapshots. For continuous streaming and frame
isolation, update to 0.3.4. The v3 encoded socket protocol is not compatible with
older launcher frame readers; update both sides rather than claiming transparent
backward compatibility.

Environment:

```text
RETUI_SESSION         session name, default retui_workspace
RETUI_INPUT           literal input for send/type
RETUI_WINDOW_NAME     optional name for new; required for launch
RETUI_COMMAND         optional initial command for new/launch
RETUI_DIRECTION       next or prev for switch
RETUI_TARGET          existing window index/name for select
RETUI_KEY             tmux key such as Tab, PageDown or C-c
RETUI_COLS/RETUI_ROWS  optional geometry, maximum 240x120
RETUI_SOCKET_NAME     abstract socket name
RETUI_TCP_PORT        loopback fallback port, default 8927
RETUI_STREAM_INTERVAL changed-frame polling interval, default 0.16 seconds
```

`kill` explicitly kills the session; it is not used when merely hiding the pane.

## Frame and socket protocol

Metadata precedes exactly one base64 payload. Terminal output is never parsed
as metadata or as a frame delimiter. Window-list entries are tab-separated,
allowing spaces in names. Example (the payload here encodes `hello` plus newline):

```text
__RETUI_BRIDGE__ retui-bridge-v1
__RETUI_VERSION__ 0.3.4
__RETUI_SESSION__ retui_workspace
__RETUI_WINDOW__ 0:1
__RETUI_WINDOWS__ 0:1*
__RETUI_COLS__ 80
__RETUI_ROWS__ 24
__RETUI_CURSOR__ 0:0
__RETUI_FRAME_BASE64__ aGVsbG8K
__RETUI_FRAME_END__
```

`socket-start` also returns `__RETUI_SOCKET__ retui-bridge-socket-v3`, the socket
name, loopback host/port and private token in its bootstrap result. The launcher
sends `HELLO <token> <cols> <rows> STREAM`. The helper returns protocol and
`__RETUI_SOCKET_STATUS__ ok`, followed by changed frames. Omitting STREAM uses
request/response captures with the **same encoded** frame format. Messages
include CAPTURE, ENTER, SEND, TYPE, NEW, SWITCH, SELECT, KEY, PING and BYE; text/name/initial
command arguments use base64. Invalid payloads fail before sending Enter.

Closing the client ends its capture loop. The listener and tmux session remain
available for reconnect; backgrounded launchers do not continue frame polling.

## Regression checks

```sh
python3 scripts/test_termux_workspace.py
python3 scripts/test_termux_helper.py
sh -n termux/retui/bin/retui
sh -n termux/retui/install.sh
```

Helper checks use a uniquely named real tmux server and temporary home, not the
user's tmux server. Android protocol checks additionally exercise bounded
loopback fixtures and the real launcher parser/renderer. Neither substitutes
for verifying interactive applications against an installed Termux backend.
