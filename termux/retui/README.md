# Re:T-UI Termux Bridge

`retui` is the Termux-side bridge used by the Re:T-UI launcher Termux workspace.
Version `0.3.2` manages a dedicated tmux session, emits a stable frame protocol,
and can expose a token-protected abstract Android local socket for live workspace
input plus changed-frame streaming, with a token-protected `127.0.0.1` fallback
for Android builds that block cross-app abstract sockets.

The launcher still bootstraps through Termux `RUN_COMMAND`. The v2 socket path is
local IPC only: Termux owns tmux and the socket daemon, while the launcher owns
the terminal surface and input routing.

## Install

From Termux:

```sh
pkg install curl tmux socat -y
curl -fsSL https://raw.githubusercontent.com/DvilSpawn/Re-TUI/master/termux/retui/install.sh | sh
```

For a development branch, replace `main` in the install URL, or set
`RETUI_INSTALL_BASE_URL` to the matching `termux/retui` directory before
running `install.sh`.

## Test

```sh
retui version
retui bridge status
retui bridge capture
RETUI_COLS=80 RETUI_ROWS=24 retui bridge socket-start
RETUI_INPUT='echo retui-ok' retui bridge send
RETUI_WINDOW_NAME='htop' RETUI_COMMAND='htop' retui bridge new
RETUI_KEY=Tab retui bridge key
```

## Bridge Commands

```text
retui bridge status
retui bridge capture
retui bridge socket-start
retui bridge socket-daemon
retui bridge socket-client
retui bridge tcp-daemon
retui bridge send
retui bridge new
retui bridge switch
retui bridge key
retui bridge kill
```

Environment:

```text
RETUI_SESSION       tmux session name, default retui_workspace
RETUI_INPUT         text sent by bridge send
RETUI_WINDOW_NAME   optional name for bridge new
RETUI_COMMAND       optional command sent after bridge new creates a window
RETUI_DIRECTION     next or prev for bridge switch
RETUI_KEY           tmux key for bridge key, such as Tab, PageDown, C-c, or -
RETUI_COLS          optional tmux width
RETUI_ROWS          optional tmux height
RETUI_SOCKET_NAME   optional abstract local socket name
RETUI_TCP_PORT      optional loopback fallback port, default 8927
RETUI_STREAM_INTERVAL optional socket stream interval in seconds, default 0.16
```

## Frame Protocol

The bridge prints metadata lines followed by a captured pane:

```text
__RETUI_BRIDGE__ retui-bridge-v1
__RETUI_VERSION__ 0.3.2
__RETUI_SESSION__ retui_workspace
__RETUI_WINDOW__ 0:1
__RETUI_WINDOWS__ 0:1*
__RETUI_TCP_HOST__ 127.0.0.1
__RETUI_TCP_PORT__ 8927
__RETUI_COLS__ 80
__RETUI_ROWS__ 24
__RETUI_CURSOR__ 0:0
__RETUI_FRAME_BEGIN__
...
__RETUI_FRAME_END__
```

`retui bridge socket-start` prints `__RETUI_SOCKET_NAME__` and `__RETUI_TOKEN__`
for the launcher bootstrap result, then starts `retui bridge socket-daemon` and
`retui bridge tcp-daemon` in Termux. The preferred socket is an abstract Android
local socket; the TCP daemon binds only to `127.0.0.1` for devices that deny
cross-app abstract sockets.

When the launcher sends `HELLO <token> <cols> <rows> STREAM`, the Termux-side
socket client starts a changed-frame stream and command messages only mutate the
Termux tmux session. Older launcher clients can omit `STREAM`; the socket then
keeps the previous request/response capture behavior. The launcher still has a
legacy fallback that talks directly to tmux when `retui` is not installed or the
socket path is unavailable.

`NEW <cols> <rows> <name> [command]` remains backward-compatible. Newer
launcher builds can pass an optional command payload so quick launchers create a
named tmux window and then send the initial command into the shell.
