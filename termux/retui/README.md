# Re:T-UI Termux Bridge

`retui` is the Termux-side bridge used by the Re:T-UI launcher Termux workspace.
Version `0.1.0` manages a dedicated tmux session and emits a stable frame protocol
for the launcher.

This is a v1 bridge. It is still command/result based through Termux
`RUN_COMMAND`, but it gives the launcher one stable CLI surface that can evolve
to a socket or WebSocket daemon later.

## Install

From Termux:

```sh
pkg install curl tmux -y
curl -fsSL https://raw.githubusercontent.com/DvilSpawn/Re-TUI/main/termux/retui/install.sh | sh
```

For a development branch:

```sh
RETUI_INSTALL_BASE_URL=https://raw.githubusercontent.com/DvilSpawn/Re-TUI/dvil/termux-workspace-page/termux/retui \
  sh -c "$(curl -fsSL https://raw.githubusercontent.com/DvilSpawn/Re-TUI/dvil/termux-workspace-page/termux/retui/install.sh)"
```

## Test

```sh
retui version
retui bridge status
retui bridge capture
RETUI_INPUT='echo retui-ok' retui bridge send
```

## Bridge Commands

```text
retui bridge status
retui bridge capture
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
RETUI_DIRECTION     next or prev for bridge switch
RETUI_KEY           tmux key for bridge key, such as C-c
```

## Frame Protocol

The bridge prints metadata lines followed by a captured pane:

```text
__RETUI_BRIDGE__ retui-bridge-v1
__RETUI_VERSION__ 0.1.0
__RETUI_SESSION__ retui_workspace
__RETUI_WINDOW__ 0:1
__RETUI_WINDOWS__ 0:1*
__RETUI_FRAME_BEGIN__
...
__RETUI_FRAME_END__
```

The launcher still has a legacy fallback that talks directly to tmux when
`retui` is not installed.
